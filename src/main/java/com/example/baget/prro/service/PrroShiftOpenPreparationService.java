package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.protocol.PrroShiftOpenDatBuilder;
import com.example.baget.prro.protocol.PrroShiftOpenEnvelopeBuilder;
import com.example.baget.users.User;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Prepares an idempotent shift-open SERVICECHK envelope for local IIT signing.
 * The cashier's private key never leaves the browser.
 */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftOpenPreparationService {
    private static final DateTimeFormatter DPS_DATE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final ZoneId UKRAINE = ZoneId.of("Europe/Kyiv");

    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;
    private final PrroOperationService operations;
    private final PrroShiftOpenEnvelopeBuilder envelopeBuilder;

    @Autowired
    public PrroShiftOpenPreparationService(PrroAccessService access, JdbcTemplate db,
                                           DpsPrroGrpcClient client,
                                           PrroOperationService operations) {
        this(access, db, client, operations, new PrroShiftOpenEnvelopeBuilder());
    }

    PrroShiftOpenPreparationService(PrroAccessService access, JdbcTemplate db,
                                    DpsPrroGrpcClient client, PrroOperationService operations,
                                    PrroShiftOpenEnvelopeBuilder envelopeBuilder) {
        this.access = Objects.requireNonNull(access, "access");
        this.db = Objects.requireNonNull(db, "db");
        this.client = Objects.requireNonNull(client, "client");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.envelopeBuilder = Objects.requireNonNull(envelopeBuilder, "envelopeBuilder");
    }

    public PreparationResult prepare(PrepareRequest request, Authentication authentication) {
        Objects.requireNonNull(request, "request");
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, request.branchNo());

        byte[] rroSignature = decodeSignature(request.rroSignatureBase64());
        List<Device> devices = db.query("""
                SELECT id, rro_fn, environment, active_shift_key
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(rs.getLong("id"), rs.getString("rro_fn"),
                rs.getString("environment"), rs.getString("active_shift_key")), request.branchNo());

        if (devices.isEmpty()) {
            throw conflict("Для цієї філії не налаштовано активний ПРРО.");
        }
        if (devices.size() != 1) {
            throw conflict("Для філії налаштовано кілька ПРРО. Потрібно визначити касу.");
        }

        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw conflict("Фіскальний номер не відповідає ПРРО, налаштованому для цієї філії.");
        }

        String signerKeyId = request.signerKeyId().trim();
        String requestHash = sha256((request.branchNo() + "|" + device.id() + "|"
                + device.rroFn() + "|" + signerKeyId).getBytes(StandardCharsets.UTF_8));
        String shiftKey = UUID.nameUUIDFromBytes(("baget-prro-shift-open:"
                + device.id() + ":" + request.idempotencyKey()).getBytes(StandardCharsets.UTF_8)).toString();

        PrroOperationService.Operation existing =
                operations.findByIdempotencyKey(device.id(), request.idempotencyKey());
        if (existing != null) {
            if (!"SHIFT_OPEN".equals(existing.sourceType())
                    || !existing.requestHash().equalsIgnoreCase(requestHash)
                    || !Objects.equals(existing.signerKeyId(), signerKeyId)
                    || !existing.shiftKey().equals(shiftKey)) {
                throw conflict("Ключ ідемпотентності вже використано для іншої PRRO-операції.");
            }
            if (existing.status() != PrroOperationService.Status.PREPARED) {
                throw conflict("Операція відкриття зміни вже перейшла у стан "
                        + existing.status() + ". Повторна підготовка заборонена.");
            }
            if (!Objects.equals(device.activeShiftKey(), existing.shiftKey())) {
                throw conflict("Локальний стан зміни не відповідає збереженій операції відкриття.");
            }
            PrroOperationService.PreparedSigningInput saved =
                    operations.getPreparedSigningInput(existing.id());
            if (saved != null) {
                return result(existing, device, saved);
            }
        } else if (device.activeShiftKey() != null) {
            throw conflict("У локальному стані вже є активна зміна ПРРО. Спочатку звірте її стан із ДПС.");
        }

        CheckRequest signedRroRequest = CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(rroSignature))
                .build();
        final RroInfoResponse info;
        final StatusResponse status;
        final CheckResponse lastCheck;
        try {
            info = client.getRroInfo(signedRroRequest);
            status = client.getRroStatus(signedRroRequest);
            lastCheck = client.getLastCheck(signedRroRequest);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не вдалося отримати інформацію, стан або останній чек ПРРО від ДПС.", ex);
        }

        if (info.getStatus() != RroInfoResponse.Status.OK
                || status.getStatus() != StatusResponse.Status.OK) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не підтвердила реєстрацію та стан ПРРО. "
                            + safeDpsMessage(status.getErrorMessage()));
        }
        if (info.getStatusRro() != 1) {
            throw conflict("ДПС не підтвердила активну реєстрацію цього ПРРО.");
        }
        boolean signerRegistered = info.getOperatorsList().stream()
                .anyMatch(operator -> signerKeyId.equalsIgnoreCase(operator.getSerial()));
        if (!signerRegistered) {
            throw conflict("Вказаний сертифікат касира не знайдено серед операторів, зареєстрованих у цьому ПРРО.");
        }
        if (info.getOpenShift() || status.getOpenShift()) {
            throw conflict("За даними ДПС зміна вже відкрита. Потрібна звірка локального стану.");
        }
        if (!info.getOnline() || !status.getOnline()) {
            throw conflict("ПРРО не перебуває в онлайн-режимі. Відкриття зміни цим сценарієм неможливе.");
        }
        if (lastCheck.getStatus() != CheckResponse.Status.OK) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не повернула останній чек для побудови hash chain: "
                            + safeDpsMessage(lastCheck.getErrorMessage()));
        }

        String taxpayerNumber = info.getTins() == null ? "" : info.getTins().trim();
        if (taxpayerNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "У відповіді infoRro ДПС відсутній податковий номер платника (TN).");
        }

        byte[] previousSignedXml = lastCheck.getDataSign().toByteArray();
        if (previousSignedXml.length == 0) {
            previousSignedXml = null;
        }
        String previousHash = previousSignedXml == null
                ? null : sha256(previousSignedXml);

        PrroOperationService.Operation operation = existing;
        if (operation == null) {
            operation = operations.prepare(new PrroOperationService.PrepareOperation(
                    device.id(), shiftKey, request.idempotencyKey(), requestHash,
                    "SHIFT_OPEN", null, "SERVICECHK", authentication.getName(), signerKeyId));
            PrroOperationService.PreparedSigningInput raced =
                    operations.getPreparedSigningInput(operation.id());
            if (raced != null) {
                return result(operation, device, raced);
            }
        }

        LocalDateTime timestamp = LocalDateTime.now(UKRAINE).withNano(0);
        long dpsDateTime = Long.parseLong(DPS_DATE_TIME.format(timestamp));
        byte[] signingInput = envelopeBuilder.build(
                new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                        device.rroFn(), taxpayerNumber, operation.id(), timestamp),
                previousSignedXml);
        String signingInputHash = sha256(signingInput);

        PrroOperationService.PreparedSigningInput stored = operations.storeSigningInput(
                operation.id(), signingInput, signingInputHash, previousHash, dpsDateTime);
        return result(operation, device, stored);
    }

    private static PreparationResult result(PrroOperationService.Operation operation,
                                             Device device,
                                             PrroOperationService.PreparedSigningInput input) {
        return new PreparationResult(operation.id(), operation.shiftKey(), device.id(),
                device.rroFn(), operation.localNumber(), operation.signerKeyId(),
                operation.status().name(), input.dpsDateTime(),
                Base64.getEncoder().encodeToString(input.bytes()),
                input.sha256(), input.previousDocumentHash());
    }

    private static byte[] decodeSignature(String encoded) {
        try {
            byte[] signature = Base64.getDecoder().decode(encoded);
            if (signature.length == 0 || signature.length > 64 * 1024) {
                throw new IllegalArgumentException("Invalid signature length");
            }
            return signature;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис фіскального номера ПРРО має бути коректним Base64 розміром до 64 КБ.", ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String safeDpsMessage(String... messages) {
        return java.util.Arrays.stream(messages)
                .filter(message -> message != null && !message.isBlank())
                .map(message -> message.length() <= 300 ? message : message.substring(0, 300))
                .reduce((left, right) -> left + " | " + right)
                .orElse("Деталі відсутні.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record PrepareRequest(@Positive long branchNo,
                                @NotBlank @Size(max = 32) String fiscalNumber,
                                @NotBlank @Size(max = 100_000) String rroSignatureBase64,
                                @NotBlank @Size(max = 128) String signerKeyId,
                                @NotBlank @Size(max = 100) String idempotencyKey) {
        public PrepareRequest {
            if (branchNo <= 0) throw new IllegalArgumentException("branchNo must be positive");
            if (fiscalNumber == null || fiscalNumber.isBlank() || fiscalNumber.length() > 32) {
                throw new IllegalArgumentException("fiscalNumber is required");
            }
            if (rroSignatureBase64 == null || rroSignatureBase64.isBlank()
                    || rroSignatureBase64.length() > 100_000) {
                throw new IllegalArgumentException("rroSignatureBase64 is required");
            }
            if (signerKeyId == null || signerKeyId.isBlank() || signerKeyId.length() > 128) {
                throw new IllegalArgumentException("signerKeyId is required");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
                throw new IllegalArgumentException("idempotencyKey is required");
            }
        }
    }

    public record PreparationResult(long operationId, String shiftKey, long deviceId,
                                    String fiscalNumber, int localNumber, String signerKeyId,
                                    String status, long dateTime, String signingInputBase64,
                                    String signingInputSha256, String previousDocumentSha256) {}

    private record Device(long id, String rroFn, String environment, String activeShiftKey) {}
}
