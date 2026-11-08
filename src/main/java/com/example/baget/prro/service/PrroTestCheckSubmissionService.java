package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.config.PrroProperties;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.Check;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Objects;

/** Submits a locally IIT-signed TEST CHK document to the DPS test endpoint. */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroTestCheckSubmissionService {

    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final PrroProperties properties;
    private final PrroOperationService operations;
    private final PrroSubmissionService submission;

    public PrroTestCheckSubmissionService(PrroAccessService access, JdbcTemplate db,
                                          PrroProperties properties, PrroOperationService operations,
                                          PrroSubmissionService submission) {
        this.access = Objects.requireNonNull(access);
        this.db = Objects.requireNonNull(db);
        this.properties = Objects.requireNonNull(properties);
        this.operations = Objects.requireNonNull(operations);
        this.submission = Objects.requireNonNull(submission);
    }

    public SubmissionResult submit(SubmitRequest request, Authentication authentication) {
        return submit(request, "TEST", null, authentication);
    }

    public SubmissionResult submitPayment(String paymentRequestKey, SubmitRequest request,
                                          Authentication authentication) {
        if (paymentRequestKey == null || paymentRequestKey.isBlank() || paymentRequestKey.length() > 36) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некоректний ключ платежу.");
        }
        return submit(request, "PAYMENT", paymentRequestKey, authentication);
    }

    private SubmissionResult submit(SubmitRequest request, String expectedSource,
                                    String expectedPaymentRequestKey, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        var operation = operations.get(request.operationId());
        if (!expectedSource.equals(operation.sourceType()) || !"CHK".equals(operation.documentType())
                || (expectedPaymentRequestKey != null
                && !expectedPaymentRequestKey.equals(operation.sourceReference()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PRRO-операція не відповідає джерелу платежу або документу CHK.");
        }

        Device device = db.query("""
                SELECT branch_no, rro_fn, active_shift_key, active_signer_key_id
                  FROM prro_devices WHERE id = ?
                """, rs -> rs.next() ? new Device(rs.getLong("branch_no"), rs.getString("rro_fn"),
                rs.getString("active_shift_key"), rs.getString("active_signer_key_id")) : null, operation.deviceId());
        if (device == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ПРРО не знайдено.");
        access.requireBranchAccess(user, device.branchNo());

        if (!properties.isTestEndpoint()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Тестовий чек дозволено надсилати лише на cabinet.tax.gov.ua:9443.");
        }
        if (!Objects.equals(device.activeShiftKey(), operation.shiftKey())
                || !Objects.equals(device.activeSignerKeyId(), operation.signerKeyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Підготовлений чек не відповідає активній зміні або її підписувачу.");
        }
        if (!Objects.equals(operation.signerKeyId(), request.signerKeyId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Сертифікат підписувача не відповідає підготовленій операції.");
        }
        if (operation.status() != PrroOperationService.Status.PREPARED) {
            return result(operations.get(operation.id()));
        }
        PrroOperationService.PreparedSigningInput prepared =
                operations.getPreparedSigningInput(operation.id());
        if (prepared == null || prepared.dpsDateTime() <= 0 || prepared.bytes().length == 0
                || operation.localNumber() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Підготовлені параметри тестового CHK відсутні або некоректні.");
        }

        final byte[] signedBytes;
        try {
            signedBytes = Base64.getDecoder().decode(request.signedPayloadBase64());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис IIT має бути коректним Base64.", ex);
        }
        if (signedBytes.length == 0 || signedBytes.length > 256 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некоректний розмір підпису IIT.");
        }

        String signedHash = sha256(signedBytes);
        operations.transition(operation.id(), PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.SIGNED, signedHash, null, null, null);

        Check check = Check.newBuilder()
                .setRroFn(device.rroFn())
                .setDateTime(prepared.dpsDateTime())
                .setCheckSign(ByteString.copyFrom(signedBytes))
                .setLocalNumber(operation.localNumber())
                .setCheckType(Check.Type.CHK)
                .build();
        try {
            return result(submission.submitSignedDocument(operation.id(), check));
        } catch (PrroSubmissionService.SubmissionOutcomeUnknownException ex) {
            // Do not retry automatically: inspection/reconciliation must resolve this operation.
            return result(operations.get(operation.id()));
        }
    }

    private static SubmissionResult result(PrroOperationService.Operation operation) {
        return new SubmissionResult(operation.id(), operation.status().name(),
                operation.responseCode(), operation.responseMessage(),
                operation.fiscalDocumentId(), operation.signedDocumentHash());
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record SubmitRequest(long operationId, String signerKeyId, String signedPayloadBase64) {
        public SubmitRequest {
            if (operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
            if (signerKeyId == null || signerKeyId.isBlank() || signerKeyId.length() > 128)
                throw new IllegalArgumentException("signerKeyId is required");
            if (signedPayloadBase64 == null || signedPayloadBase64.isBlank()
                    || signedPayloadBase64.length() > 400_000)
                throw new IllegalArgumentException("signedPayloadBase64 is required");
        }
    }

    public record SubmissionResult(long operationId, String status, Integer responseCode,
                                   String responseMessage, String fiscalDocumentId,
                                   String signedPayloadSha256) {}

    private record Device(long branchNo, String rroFn,
                          String activeShiftKey, String activeSignerKeyId) {}
}
