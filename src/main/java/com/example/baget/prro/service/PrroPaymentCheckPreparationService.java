package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.config.PrroProperties;
import com.example.baget.prro.protocol.PrroCheckEnvelopeBuilder;
import com.example.baget.prro.protocol.PrroXmlDocumentBuilder;
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

/**
 * Prepares a CHK for a real, already-recorded Baget incoming payment,
 * but deliberately permits submission only to the DPS TEST endpoint.
 *
 * The receipt represents the newly received money only. Allocated customer
 * advance is not recorded as a second receipt, and customer PII is omitted.
 */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroPaymentCheckPreparationService {
    private static final String TEST_HOST = "cabinet.tax.gov.ua";
    private static final int TEST_PORT = 9443;
    private static final String TEST_TAX_CODE = "Н";
    private static final ZoneId UKRAINE = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter DPS_DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;
    private final PrroProperties properties;
    private final PrroOperationService operations;
    private final PrroPaymentOperationService paymentOperations;
    private final PrroCheckEnvelopeBuilder envelopeBuilder;
    private final PrroXmlDocumentBuilder documentBuilder = new PrroXmlDocumentBuilder();

    public PrroPaymentCheckPreparationService(PrroAccessService access, JdbcTemplate db,
                                               DpsPrroGrpcClient client, PrroProperties properties,
                                               PrroOperationService operations,
                                               PrroPaymentOperationService paymentOperations,
                                               PrroCheckEnvelopeBuilder envelopeBuilder) {
        this.access = Objects.requireNonNull(access);
        this.db = Objects.requireNonNull(db);
        this.client = Objects.requireNonNull(client);
        this.properties = Objects.requireNonNull(properties);
        this.operations = Objects.requireNonNull(operations);
        this.paymentOperations = Objects.requireNonNull(paymentOperations);
        this.envelopeBuilder = Objects.requireNonNull(envelopeBuilder);
    }

    public PreparationResult prepare(String paymentRequestKey, PrepareRequest request,
                                     Authentication authentication) {
        UserContext user = requireUserAndBranch(request, authentication);
        Device device = findDevice(request.branchNo());
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw conflict("Фіскальний номер не відповідає ПРРО цієї філії.");
        }
        requireTestTarget(device.environment());

        String signerKeyId = request.signerKeyId().trim();
        if (device.activeShiftKey() == null || device.activeShiftKey().isBlank()) {
            throw conflict("Спочатку відкрийте та звірте тестову зміну ПРРО.");
        }
        if (!Objects.equals(device.activeSignerKeyId(), signerKeyId)) {
            throw conflict("Тестовий чек має бути підписаний тим самим касиром, який відкрив зміну.");
        }

        byte[] rroSignature = decodeSignature(request.rroSignatureBase64());
        PrroPaymentOperationService.PaymentReceiptSource source =
                paymentOperations.receiptSource(paymentRequestKey, request.branchNo(), authentication);

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
                    "ДПС не повернула останній документ для hash chain: "
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

        PrroPaymentOperationService.PaymentOperationView linked =
                paymentOperations.prepare(paymentRequestKey, request.branchNo(), authentication);
        if (linked.operationId() == null) {
            throw conflict("Не вдалося створити локальну PRRO-операцію для платежу.");
        }
        PrroOperationService.Operation operation = operations.get(linked.operationId());
        if (!"PAYMENT".equals(operation.sourceType())
                || !paymentRequestKey.equals(operation.sourceReference())
                || !"CHK".equals(operation.documentType())) {
            throw conflict("PRRO-операція не відповідає вибраному платежу.");
        }
        if (!Objects.equals(operation.shiftKey(), device.activeShiftKey())
                || !Objects.equals(operation.signerKeyId(), signerKeyId)) {
            throw conflict("Платіж прив'язаний до іншої зміни або сертифіката касира.");
        }
        if (operation.status() != PrroOperationService.Status.PREPARED) {
            throw conflict("Платіж уже перейшов у стан " + operation.status()
                    + ". Повторне підписання або надсилання заборонене.");
        }

        PrroOperationService.PreparedSigningInput saved = operations.getPreparedSigningInput(operation.id());
        if (saved != null) return result(operation, device, saved);

        LocalDateTime timestamp = LocalDateTime.now(UKRAINE).withNano(0);
        long dpsDateTime = Long.parseLong(DPS_DATE_TIME.format(timestamp));
        PrroXmlDocumentBuilder.PacketHeader header = new PrroXmlDocumentBuilder.PacketHeader(
                device.rroFn(), taxpayerNumber, device.rroFn(), operation.id(), timestamp);

        PrroXmlDocumentBuilder.PaymentType paymentType =
                "CASH".equals(source.paymentType())
                        ? PrroXmlDocumentBuilder.PaymentType.CASH
                        : PrroXmlDocumentBuilder.PaymentType.OTHER;
        PrroXmlDocumentBuilder.CheckDocument check = new PrroXmlDocumentBuilder.CheckDocument(
                header,
                PrroXmlDocumentBuilder.ReceiptType.SALE,
                List.of(new PrroXmlDocumentBuilder.SaleItem(
                        1, null, source.documentLabel(), source.amount(),
                        BigDecimal.ONE, null, TEST_TAX_CODE, null)),
                List.of(new PrroXmlDocumentBuilder.Payment(
                        2, paymentType, source.paymentName(), source.amount())),
                new PrroXmlDocumentBuilder.ReceiptClosing(
                        3, operation.localNumber(), source.amount(), source.amount(), null,
                        List.of(new PrroXmlDocumentBuilder.TaxBreakdown(
                                TEST_TAX_CODE, null, null, null, null, null, null))));

        byte[] signingInput = envelopeBuilder.build(documentBuilder.buildCheck(check), previousSigningInput);
        PrroOperationService.PreparedSigningInput stored = operations.storeSigningInput(
                operation.id(), signingInput, sha256(signingInput),
                sha256(previousSigningInput), dpsDateTime);
        return result(operation, device, stored);
    }

    private UserContext requireUserAndBranch(PrepareRequest request, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, request.branchNo());
        return new UserContext(user.getUsername());
    }

    private Device findDevice(long branchNo) {
        List<Device> devices = db.query("""
                SELECT id, rro_fn, environment, active_shift_key, active_signer_key_id
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(rs.getLong("id"), rs.getString("rro_fn"),
                rs.getString("environment"), rs.getString("active_shift_key"),
                rs.getString("active_signer_key_id")), branchNo);
        if (devices.isEmpty()) throw conflict("Для цієї філії не налаштовано активний ПРРО.");
        if (devices.size() != 1) throw conflict("Для філії налаштовано кілька ПРРО.");
        return devices.get(0);
    }

    private void requireTestTarget(String environment) {
        if (!"TEST".equalsIgnoreCase(environment)
                || !TEST_HOST.equalsIgnoreCase(properties.getHost())
                || properties.getPort() != TEST_PORT || !properties.isTlsEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Тестові платежі дозволено надсилати лише для TEST-ПРРО на cabinet.tax.gov.ua:9443.");
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
                                 @NotBlank @Size(max = 128) String signerKeyId) {}
    public record PreparationResult(long operationId, String shiftKey, long deviceId, String fiscalNumber,
                                    int localNumber, String signerKeyId, String status, long dateTime,
                                    String signingInputBase64, String signingInputSha256,
                                    String previousDocumentSha256) {}
    private record Device(long id, String rroFn, String environment, String activeShiftKey,
                          String activeSignerKeyId) {}
    private record UserContext(String username) {}
}
