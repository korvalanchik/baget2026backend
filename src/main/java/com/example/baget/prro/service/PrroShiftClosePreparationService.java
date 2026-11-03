package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.config.PrroProperties;
import com.example.baget.prro.protocol.PrroXmlDocumentBuilder;
import com.example.baget.prro.protocol.PrroZReportEnvelopeBuilder;
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

@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftClosePreparationService {
    private static final String TEST_HOST = "cabinet.tax.gov.ua";
    private static final int TEST_PORT = 9443;
    private static final ZoneId UKRAINE = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter DPS_DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;
    private final PrroProperties properties;
    private final PrroOperationService operations;
    private final PrroZReportEnvelopeBuilder envelopeBuilder;
    private final PrroXmlDocumentBuilder documentBuilder = new PrroXmlDocumentBuilder();

    public PrroShiftClosePreparationService(PrroAccessService access, JdbcTemplate db,
                                            DpsPrroGrpcClient client, PrroProperties properties,
                                            PrroOperationService operations,
                                            PrroZReportEnvelopeBuilder envelopeBuilder) {
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
                SELECT id, rro_fn, environment, active_shift_key, active_signer_key_id,
                       next_local_number
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(
                rs.getLong("id"), rs.getString("rro_fn"), rs.getString("environment"),
                rs.getString("active_shift_key"), rs.getString("active_signer_key_id"),
                rs.getInt("next_local_number")), request.branchNo());

        if (devices.isEmpty()) throw notFound("Для цієї філії не налаштовано активний ПРРО.");
        if (devices.size() != 1) throw conflict("Для філії налаштовано кілька ПРРО.");
        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw conflict("Фіскальний номер не відповідає ПРРО цієї філії.");
        }
        requireTestTarget(device.environment());

        if (device.activeShiftKey() == null || device.activeShiftKey().isBlank()) {
            throw conflict("Локальна зміна не відкрита. Спочатку звірте стан ПРРО з ДПС.");
        }
        if (device.activeSignerKeyId() == null || device.activeSignerKeyId().isBlank()) {
            throw conflict("Для активної зміни не збережено ідентифікатор підписувача.");
        }
        String signerKeyId = request.signerKeyId().trim();
        if (!device.activeSignerKeyId().equals(signerKeyId)) {
            throw conflict("Закриття тестової зміни дозволено тим самим підписувачем, який її відкрив.");
        }

        String requestHash = sha256((request.branchNo() + "|" + device.id() + "|"
                + device.rroFn() + "|" + device.activeShiftKey() + "|" + signerKeyId)
                .getBytes(StandardCharsets.UTF_8));
        PrroOperationService.Operation existing =
                operations.findByIdempotencyKey(device.id(), request.idempotencyKey());
        if (existing != null) {
            if (!"SHIFT_CLOSE".equals(existing.sourceType())
                    || !"ZREPORT".equals(existing.documentType())
                    || !existing.requestHash().equalsIgnoreCase(requestHash)
                    || !Objects.equals(existing.signerKeyId(), signerKeyId)
                    || !existing.shiftKey().equals(device.activeShiftKey())) {
                throw conflict("Ключ ідемпотентності вже використано для іншої PRRO-операції.");
            }
            if (existing.status() != PrroOperationService.Status.PREPARED) {
                throw conflict("Операція закриття вже перейшла у стан " + existing.status()
                        + ". Повторне надсилання заборонено.");
            }
            PrroOperationService.PreparedSigningInput saved =
                    operations.getPreparedSigningInput(existing.id());
            if (saved != null) return result(existing, device, saved);
        }

        // Until ZREPORT turnover aggregation is implemented, block every fiscalized sale (PAYMENT and TEST).
        Long fiscalizedSales = db.queryForObject("""
                SELECT COUNT(*)
                  FROM prro_operations
                 WHERE device_id = ? AND shift_key = ?
                   AND source_type IN ('PAYMENT', 'TEST') AND document_type = 'CHK'
                   AND status = 'FISCALIZED'
                """, Long.class, device.id(), device.activeShiftKey());
        if (fiscalizedSales != null && fiscalizedSales > 0) {
            throw conflict("ZREPORT поки не вміє агрегувати обороти продажних чеків (PAYMENT/TEST). Закриття заблоковано, щоб не сформувати нульовий звіт.");
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
                    "Не вдалося отримати стан ПРРО або останній документ від ДПС.", ex);
        }

        if (info.getStatus() != RroInfoResponse.Status.OK
                || status.getStatus() != StatusResponse.Status.OK) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не підтвердила стан ПРРО. " + safeMessage(status.getErrorMessage()));
        }
        if (info.getStatusRro() != 1) throw conflict("ДПС не підтвердила активну реєстрацію ПРРО.");
        if (!info.getOpenShift() || !status.getOpenShift()) {
            throw conflict("ДПС не підтверджує відкриту зміну. Закриття скасовано.");
        }
        if (!info.getOnline() || !status.getOnline()) {
            throw conflict("ПРРО має бути онлайн для тестового закриття зміни.");
        }
        if (!info.getOperatorsList().stream().anyMatch(operator ->
                signerKeyId.equalsIgnoreCase(operator.getSerial()))) {
            throw conflict("Підписувача не знайдено серед операторів, зареєстрованих у ПРРО.");
        }
        if (status.getLastSigner() != null && !status.getLastSigner().isBlank()
                && !signerKeyId.equalsIgnoreCase(status.getLastSigner())) {
            throw conflict("Підписувач у statusRro не збігається з підписувачем активної зміни.");
        }
        if (lastCheck.getStatus() != CheckResponse.Status.OK
                || lastCheck.getDataSign().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не повернула попередній підписаний документ для ZREPORT: "
                            + safeMessage(lastCheck.getErrorMessage()));
        }

        String expectedPreviousId = db.query("""
                SELECT fiscal_document_id
                  FROM prro_operations
                 WHERE device_id = ? AND shift_key = ? AND status = 'FISCALIZED'
                 ORDER BY local_number DESC, id DESC
                 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, device.id(), device.activeShiftKey());
        if (expectedPreviousId == null || !expectedPreviousId.equals(lastCheck.getId())) {
            throw conflict("lastChk не відповідає останньому локально фіскалізованому документу зміни. Потрібна звірка.");
        }

        String taxpayerNumber = info.getTins() == null ? "" : info.getTins().trim();
        if (taxpayerNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "У відповіді infoRro відсутній податковий номер платника (TN).");
        }

        byte[] previousSigningInput =
                operations.getFiscalizedSigningInput(device.id(), lastCheck.getId());
        if (previousSigningInput == null || previousSigningInput.length == 0) {
            throw conflict("Для останнього документа ДПС не знайдено локальний signing_input. "
                    + "Закриття зміни зупинено, щоб не сформувати неправильний MAC.");
        }
        String previousHash = sha256(previousSigningInput);
        PrroOperationService.Operation operation = existing;
        if (operation == null) {
            operation = operations.prepare(new PrroOperationService.PrepareOperation(
                    device.id(), device.activeShiftKey(), request.idempotencyKey(), requestHash,
                    "SHIFT_CLOSE", null, "ZREPORT", authentication.getName(), signerKeyId));
        }

        LocalDateTime timestamp = LocalDateTime.now(UKRAINE).withNano(0);
        long dpsDateTime = Long.parseLong(DPS_DATE_TIME.format(timestamp));
        Long previousReports = db.queryForObject("""
                SELECT COUNT(*)
                  FROM prro_operations
                 WHERE device_id = ? AND document_type = 'ZREPORT' AND status = 'FISCALIZED'
                """, Long.class, device.id());
        long reportNumber = (previousReports == null ? 0 : previousReports) + 1;

        PrroXmlDocumentBuilder.PacketHeader header = new PrroXmlDocumentBuilder.PacketHeader(
                device.rroFn(), taxpayerNumber, "", operation.id(), timestamp);
        PrroXmlDocumentBuilder.ZReportDocument zReport =
                new PrroXmlDocumentBuilder.ZReportDocument(
                        header,
                        reportNumber,
                        List.of(),
                        List.of(new PrroXmlDocumentBuilder.PaymentTurnover(
                                PrroXmlDocumentBuilder.PaymentType.CASH, null, ZERO, ZERO)),
                        List.of(new PrroXmlDocumentBuilder.CashMovementSummary(
                                PrroXmlDocumentBuilder.PaymentType.CASH, null, ZERO, ZERO)),
                        new PrroXmlDocumentBuilder.CheckCounts(0, 0));
        String datXml = documentBuilder.buildZReport(zReport);
        byte[] signingInput = envelopeBuilder.build(datXml, previousSigningInput);
        String signingInputHash = sha256(signingInput);

        PrroOperationService.PreparedSigningInput stored = operations.storeSigningInput(
                operation.id(), signingInput, signingInputHash, previousHash, dpsDateTime);
        return result(operation, device, stored);
    }

    private void requireTestTarget(String environment) {
        if (!"TEST".equalsIgnoreCase(environment)
                || !TEST_HOST.equalsIgnoreCase(properties.getHost())
                || properties.getPort() != TEST_PORT
                || !properties.isTlsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Тестове закриття дозволене лише для TEST-ПРРО на cabinet.tax.gov.ua:9443.");
        }
    }

    private static PreparationResult result(PrroOperationService.Operation operation,
                                            Device device,
                                            PrroOperationService.PreparedSigningInput input) {
        return new PreparationResult(operation.id(), operation.shiftKey(), device.id(),
                device.rroFn(), operation.localNumber(), operation.signerKeyId(),
                operation.status().name(), input.dpsDateTime(),
                Base64.getEncoder().encodeToString(input.bytes()), input.sha256(),
                input.previousDocumentHash());
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

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "Деталі відсутні.";
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record PrepareRequest(@Positive long branchNo,
                                 @NotBlank @Size(max = 32) String fiscalNumber,
                                 @NotBlank @Size(max = 100_000) String rroSignatureBase64,
                                 @NotBlank @Size(max = 128) String signerKeyId,
                                 @NotBlank @Size(max = 100) String idempotencyKey) {
        public PrepareRequest {
            if (branchNo <= 0) throw new IllegalArgumentException("branchNo must be positive");
            if (fiscalNumber == null || fiscalNumber.isBlank() || fiscalNumber.length() > 32)
                throw new IllegalArgumentException("fiscalNumber is required");
            if (rroSignatureBase64 == null || rroSignatureBase64.isBlank()
                    || rroSignatureBase64.length() > 100_000)
                throw new IllegalArgumentException("rroSignatureBase64 is required");
            if (signerKeyId == null || signerKeyId.isBlank() || signerKeyId.length() > 128)
                throw new IllegalArgumentException("signerKeyId is required");
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100)
                throw new IllegalArgumentException("idempotencyKey is required");
        }
    }

    public record PreparationResult(long operationId, String shiftKey, long deviceId,
                                    String fiscalNumber, int localNumber, String signerKeyId,
                                    String status, long dateTime, String signingInputBase64,
                                    String signingInputSha256, String previousDocumentSha256) {}

    private record Device(long id, String rroFn, String environment, String activeShiftKey,
                          String activeSignerKeyId, int nextLocalNumber) {}
}
