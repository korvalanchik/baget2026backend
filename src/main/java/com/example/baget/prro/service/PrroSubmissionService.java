package com.example.baget.prro.service;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.programika.rro.ws.chk.Check;
import com.programika.rro.ws.chk.CheckResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Submits an already-built and signed DPS protocol document.
 *
 * XML construction and QES signing deliberately remain outside this class until
 * they are implemented against the complete current DPS protocol.
 */
@Service
public class PrroSubmissionService {

    private final PrroOperationService operations;
    private final DpsPrroGrpcClient client;

    public PrroSubmissionService(PrroOperationService operations, DpsPrroGrpcClient client) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.client = Objects.requireNonNull(client, "client");
    }

    public PrroOperationService.Operation submitSignedDocument(long operationId, Check check) {
        Objects.requireNonNull(check, "check");
        PrroOperationService.Operation operation = operations.get(operationId);

        if (operation.status() != PrroOperationService.Status.SIGNED) {
            throw new IllegalStateException("Only a SIGNED PRRO operation can be submitted");
        }
        if (operation.localNumber() != check.getLocalNumber()) {
            throw new IllegalArgumentException("Check local number does not match the prepared operation");
        }
        if (documentType(operation.documentType()) != check.getCheckType()) {
            throw new IllegalArgumentException("Check type does not match the prepared operation");
        }
        if (check.getCheckSign().isEmpty()) {
            throw new IllegalArgumentException("Signed DPS document must not be empty");
        }

        String signedHash = sha256(check.getCheckSign().toByteArray());
        if (operation.signedDocumentHash() == null
                || !operation.signedDocumentHash().equalsIgnoreCase(signedHash)) {
            throw new IllegalArgumentException("Signed document hash does not match the prepared operation");
        }

        operations.transition(operationId, PrroOperationService.Status.SIGNED,
                PrroOperationService.Status.SENDING, signedHash, null, null, null);

        final CheckResponse response;
        try {
            response = client.sendCheckV2(check);
        } catch (StatusRuntimeException ex) {
            operations.transition(operationId, PrroOperationService.Status.SENDING,
                    PrroOperationService.Status.UNKNOWN_RESULT, signedHash, null, null,
                    "gRPC result is unknown: " + safeMessage(ex));
            throw new SubmissionOutcomeUnknownException(operationId, ex);
        }

        int responseCode = response.getStatusValue();
        if (responseCode == CheckResponse.Status.OK.getNumber()) {
            return operations.transition(operationId, PrroOperationService.Status.SENDING,
                    PrroOperationService.Status.FISCALIZED, signedHash, response.getId(),
                    responseCode, response.getErrorMessage());
        }

        if (responseCode == CheckResponse.Status.UNKNOWN.getNumber()) {
            return operations.transition(operationId, PrroOperationService.Status.SENDING,
                    PrroOperationService.Status.UNKNOWN_RESULT, signedHash, null,
                    responseCode, "DPS returned an undefined status");
        }

        return operations.transition(operationId, PrroOperationService.Status.SENDING,
                PrroOperationService.Status.REJECTED, signedHash, null,
                responseCode, response.getErrorMessage().isBlank()
                        ? "DPS rejected the document with status " + responseCode
                        : response.getErrorMessage());
    }

    private static Check.Type documentType(String documentType) {
        return switch (documentType) {
            case "CHK" -> Check.Type.CHK;
            case "ZREPORT" -> Check.Type.ZREPORT;
            case "SERVICECHK" -> Check.Type.SERVICECHK;
            default -> throw new IllegalArgumentException("Unsupported PRRO document type: " + documentType);
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String safeMessage(StatusRuntimeException ex) {
        String message = ex.getStatus().getCode().name();
        if (ex.getStatus().getDescription() != null && !ex.getStatus().getDescription().isBlank()) {
            message += ": " + ex.getStatus().getDescription();
        }
        return message.length() <= 400 ? message : message.substring(0, 400);
    }

    public static class SubmissionOutcomeUnknownException extends RuntimeException {
        private final long operationId;

        public SubmissionOutcomeUnknownException(long operationId, StatusRuntimeException cause) {
            super("PRRO submission outcome is unknown; reconcile operation " + operationId
                    + " before any retry", cause);
            this.operationId = operationId;
        }

        public long getOperationId() {
            return operationId;
        }
    }
}
