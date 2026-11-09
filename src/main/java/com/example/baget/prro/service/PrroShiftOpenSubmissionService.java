package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
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

/** Experimental shift-open submission restricted to the DPS developer endpoint. */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftOpenSubmissionService {
    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final PrroOperationService operations;
    private final PrroSubmissionService submission;

    public PrroShiftOpenSubmissionService(PrroAccessService access, JdbcTemplate db,
                                          PrroOperationService operations,
                                          PrroSubmissionService submission) {
        this.access = Objects.requireNonNull(access);
        this.db = Objects.requireNonNull(db);
        this.operations = Objects.requireNonNull(operations);
        this.submission = Objects.requireNonNull(submission);
    }

    public SubmissionResult submit(SubmitRequest request, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        var operation = operations.get(request.operationId());
        if (!"SHIFT_OPEN".equals(operation.sourceType())
                || !"SERVICECHK".equals(operation.documentType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Операція не є відкриттям зміни SERVICECHK.");
        }

        Device device = db.query("""
                SELECT branch_no, rro_fn
                  FROM prro_devices
                 WHERE id = ?
                """, rs -> rs.next() ? new Device(rs.getLong("branch_no"),
                rs.getString("rro_fn")) : null, operation.deviceId());
        if (device == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ПРРО не знайдено.");
        }
        access.requireBranchAccess(user, device.branchNo());

        if (operation.status() != PrroOperationService.Status.PREPARED) {
            return result(operations.get(operation.id()));
        }
        PrroOperationService.PreparedSigningInput prepared =
                operations.getPreparedSigningInput(operation.id());
        if (prepared == null || prepared.dpsDateTime() <= 0 || operation.localNumber() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Підготовлені параметри SERVICECHK некоректні або відсутні.");
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

        if (operation.signerKeyId() == null
                || !operation.signerKeyId().equals(request.signerKeyId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Сертифікат підписувача не відповідає підготовленій операції.");
        }

        String signedHash = sha256(signedBytes);
        operations.transition(operation.id(), PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.SIGNED, signedHash, null, null, null);

        Check check = Check.newBuilder()
                .setRroFn(device.rroFn())
                .setDateTime(prepared.dpsDateTime())
                .setCheckSign(ByteString.copyFrom(signedBytes))
                .setLocalNumber(0)
                .setCheckType(Check.Type.SERVICECHK)
                .build();

        try {
            return result(submission.submitSignedDocument(operation.id(), check, request.rroSignatureBase64()));
        } catch (PrroSubmissionService.SubmissionOutcomeUnknownException ex) {
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

    public record SubmitRequest(long operationId, String signerKeyId, String signedPayloadBase64,
                               String rroSignatureBase64) {
        public SubmitRequest {
            if (operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
            if (signerKeyId == null || signerKeyId.isBlank() || signerKeyId.length() > 128) {
                throw new IllegalArgumentException("signerKeyId is required");
            }
            if (signedPayloadBase64 == null || signedPayloadBase64.isBlank()
                    || signedPayloadBase64.length() > 400_000) {
                throw new IllegalArgumentException("signedPayloadBase64 is required");
            }
            if (rroSignatureBase64 == null || rroSignatureBase64.isBlank()
                    || rroSignatureBase64.length() > 100_000)
                throw new IllegalArgumentException("rroSignatureBase64 is required");
        }
    }

    public record SubmissionResult(long operationId, String status, Integer responseCode,
                                   String responseMessage, String fiscalDocumentId,
                                   String signedPayloadSha256) {}
    private record Device(long branchNo, String rroFn) {}
}
