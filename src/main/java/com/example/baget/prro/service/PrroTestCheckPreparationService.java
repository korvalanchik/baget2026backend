package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.config.PrroProperties;
import com.example.baget.prro.protocol.PrroCheckEnvelopeBuilder;
import com.example.baget.prro.protocol.PrroXmlDocumentBuilder;
import com.example.baget.users.User;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

/** Prepares a fixed, idempotent TEST sale receipt for local IIT signing. */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroTestCheckPreparationService {
    private static final String TEST_HOST = "cabinet.tax.gov.ua";
    private static final int TEST_PORT = 9443;
    private static final ZoneId UKRAINE = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter DPS_DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final BigDecimal AMOUNT = new BigDecimal("1.00");

    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;
    private final PrroProperties properties;
    private final PrroOperationService operations;
    private final PrroCheckEnvelopeBuilder envelopeBuilder;
    private final PrroXmlDocumentBuilder documentBuilder = new PrroXmlDocumentBuilder();

    public PrroTestCheckPreparationService(PrroAccessService access, JdbcTemplate db,
                                           DpsPrroGrpcClient client, PrroProperties properties,
                                           PrroOperationService operations,
                                           PrroCheckEnvelopeBuilder envelopeBuilder) {
        this.access = Objects.requireNonNull(access);
        this.db = Objects.requireNonNull(db);
        this.client = Objects.requireNonNull(client);
        this.properties = Objects.requireNonNull(properties);
        this.operations = Objects.requireNonNull(operations);
        this.envelopeBuilder = Objects.requireNonNull(envelopeBuilder);
    }

    public PreparationResult prepare(PrepareRequest request, Authentication authentication) {
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, request.branchNo());
        byte[] rroSignature = decodeSignature(request.rroSignatureBase64());

        List<Device> devices = db.query("""
                SELECT id, rro_fn, environment, active_shift_key, active_signer_key_id
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(rs.getLong("id"), rs.getString("rro_fn"),
                rs.getString("environment"), rs.getString("active_shift_key"),
                rs.getString("active_signer_key_id")), request.branchNo());
        if (devices.isEmpty()) throw conflict("Для цієї філії не налаштовано активний ПРРО.");
        if (devices.size() != 1) throw conflict("Для філії налаштовано кілька ПРРО.");
        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw conflict("Фіскальний номер не відповідає ПРРО цієї філії.");
        }
        requireTestTarget(device.environment());
        if (device.activeShiftKey() == null || device.activeShiftKey().isBlank()) {
            throw conflict("Спочатку відкрийте та звірте тестову зміну ПРРО.");
        }
        String signerKeyId = request.signerKeyId().trim();
        if (!Objects.equals(device.activeSignerKeyId(), signerKeyId)) {
            throw conflict("Тестовий чек має бути підписаний тим самим касиром, який відкрив зміну.");
        }

        String requestHash = sha256((request.branchNo() + "|" + device.id() + "|"
                + device.rroFn() + "|" + device.activeShiftKey() + "|"
                + signerKeyId + "|TEST|CHK|Тестова послуга|1.00|Н|CASH|1.00")
                .getBytes(StandardCharsets.UTF_8));
        PrroOperationService.Operation existing =
                operations.findByIdempotencyKey(device.id(), request.idempotencyKey());
        if (existing != null) {
            if (!"TEST".equals(existing.sourceType()) || !"CHK".equals(existing.documentType())
                    || !existing.requestHash().equalsIgnoreCase(requestHash)
                    || !Objects.equals(existing.signerKeyId(), signerKeyId)
                    || !existing.shiftKey().equals(device.activeShiftKey())) {
                throw conflict("Ключ ідемпотентності вже використано для іншої PRRO-операції.");
            }
            if (existing.status() != PrroOperationService.Status.PREPARED) {
                throw conflict("Тестовий чек уже перейшов у стан " + existing.status()
                        + ". Повторне підписання або надсилання заборонене.");
            }
            PrroOperationService.PreparedSigningInput saved = operations.getPreparedSigningInput(existing.id());
            if (saved != null) return result(existing, device, saved);
        }

        CheckRequest signedRequest = CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(rroSignature)).build();
        final RroInfoResponse info;
        final StatusResponse status;
        final CheckResponse lastCheck;
        try {
            info = client.getRroInfo(signedRequest);
            status = client.getRroStatus(signedRequest);
            lastCheck = client.getLastCheck(signedRequest);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не вдалося перевірити ПРРО та останній документ у ДПС.", ex);
        }
        if (info.getStatus() != RroInfoResponse.Status.OK
                || status.getStatus() != StatusResponse.Status.OK) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не підтвердила стан ПРРО: " + safeMessage(status.getErrorMessage()));
        }
        if (info.getStatusRro() != 1) throw conflict("ДПС не підтвердила активну реєстрацію ПРРО.");
        if (!info.getOpenShift() || !status.getOpenShift()) throw conflict("За даними ДПС зміна не відкрита.");
        if (!info.getOnline() || !status.getOnline()) throw conflict("Для тестового чека ПРРО має бути онлайн.");
        if (info.getOperatorsList().stream().noneMatch(op -> signerKeyId.equalsIgnoreCase(op.getSerial()))) {
            throw conflict("Підписувача не знайдено серед операторів, зареєстрованих у ПРРО.");
        }
        if (status.getLastSigner() != null && !status.getLastSigner().isBlank()
                && !signerKeyId.equalsIgnoreCase(status.getLastSigner())) {
            throw conflict("Підписувач у statusRro не збігається з касиром активної зміни.");
        }
        if (lastCheck.getStatus() != CheckResponse.Status.OK
                || lastCheck.getId() == null || lastCheck.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не повернула останній фіскальний документ для hash chain: "
                            + safeMessage(lastCheck.getErrorMessage()));
        }

        String expectedPreviousId = db.query("""
                SELECT fiscal_document_id
                  FROM prro_operations
                 WHERE device_id = ? AND shift_key = ? AND status = 'FISCALIZED'
                 ORDER BY local_number DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, device.id(), device.activeShiftKey());
        if (!lastCheck.getId().equals(expectedPreviousId)) {
            throw conflict("lastChk не відповідає останньому локально фіскалізованому документу зміни. Потрібна звірка.");
        }
        byte[] previousSigningInput = operations.getFiscalizedSigningInput(device.id(), lastCheck.getId());
        if (previousSigningInput == null || previousSigningInput.length == 0) {
            throw conflict("Не знайдено точні байти signing_input попереднього документа. Фіскалізацію зупинено.");
        }
        String taxpayerNumber = info.getTins() == null ? "" : info.getTins().trim();
        if (taxpayerNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "У відповіді infoRro відсутній TN.");
        }

        PrroOperationService.Operation operation = existing;
        if (operation == null) {
            operation = operations.prepare(new PrroOperationService.PrepareOperation(
                    device.id(), device.activeShiftKey(), request.idempotencyKey(), requestHash,
                    "TEST", "TEST-CHECK-" + request.idempotencyKey(), "CHK",
                    authentication.getName(), signerKeyId));
        }

        LocalDateTime timestamp = LocalDateTime.now(UKRAINE).withNano(0);
        long dpsDateTime = Long.parseLong(DPS_DATE_TIME.format(timestamp));
        PrroXmlDocumentBuilder.PacketHeader header = new PrroXmlDocumentBuilder.PacketHeader(
                device.rroFn(), taxpayerNumber, device.rroFn(), operation.id(), timestamp);
        PrroXmlDocumentBuilder.CheckDocument check = new PrroXmlDocumentBuilder.CheckDocument(
                header,
                PrroXmlDocumentBuilder.ReceiptType.SALE,
                List.of(new PrroXmlDocumentBuilder.SaleItem(
                        1, null, "Тестова послуга", AMOUNT, BigDecimal.ONE, null, "Н", null)),
                List.of(new PrroXmlDocumentBuilder.Payment(
                        2, PrroXmlDocumentBuilder.PaymentType.CASH, "Готівка", AMOUNT)),
                new PrroXmlDocumentBuilder.ReceiptClosing(
                        3, operation.localNumber(), AMOUNT, AMOUNT, null,
                        List.of(new PrroXmlDocumentBuilder.TaxBreakdown(
                                "Н", null, null, null, null, null, null))));
        byte[] signingInput = envelopeBuilder.build(documentBuilder.buildCheck(check), previousSigningInput);
        PrroOperationService.PreparedSigningInput stored = operations.storeSigningInput(
                operation.id(), signingInput, sha256(signingInput),
                sha256(previousSigningInput), dpsDateTime);
        return result(operation, device, stored);
    }

    private void requireTestTarget(String environment) {
        if (!"TEST".equalsIgnoreCase(environment)
                || !TEST_HOST.equalsIgnoreCase(properties.getHost())
                || properties.getPort() != TEST_PORT || !properties.isTlsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Тестова фіскалізація дозволена лише для TEST-ПРРО на cabinet.tax.gov.ua:9443.");
        }
    }

    private static PreparationResult result(PrroOperationService.Operation operation, Device device,
                                            PrroOperationService.PreparedSigningInput input) {
        return new PreparationResult(operation.id(), operation.shiftKey(), device.id(), device.rroFn(),
                operation.localNumber(), operation.signerKeyId(), operation.status().name(),
                input.dpsDateTime(), Base64.getEncoder().encodeToString(input.bytes()),
                input.sha256(), input.previousDocumentHash());
    }

    private static byte[] decodeSignature(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > 64 * 1024) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис фіскального номера ПРРО має бути коректним Base64 до 64 КБ.", ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "Деталі відсутні.";
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record PrepareRequest(@Positive long branchNo,
                                 @NotBlank @Size(max = 32) String fiscalNumber,
                                 @NotBlank @Size(max = 100_000) String rroSignatureBase64,
                                 @NotBlank @Size(max = 128) String signerKeyId,
                                 @NotBlank @Size(max = 100) String idempotencyKey) {}

    public record PreparationResult(long operationId, String shiftKey, long deviceId,
                                    String fiscalNumber, int localNumber, String signerKeyId,
                                    String status, long dateTime, String signingInputBase64,
                                    String signingInputSha256, String previousDocumentSha256) {}

    private record Device(long id, String rroFn, String environment, String activeShiftKey,
                          String activeSignerKeyId) {}
}
