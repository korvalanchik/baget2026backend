package com.example.baget.prro.service;

import com.example.baget.prro.signing.PrroDocumentSigner;
import com.example.baget.prro.signing.PrroDocumentSigner.SignedPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Signs a prepared PRRO operation through an injected signing adapter, checks
 * the actual signer identity, and records the signed payload hash.
 *
 * <p>This service never submits a document to DPS. There is intentionally no
 * production signer adapter until the exact DPS signature/hash-chain contract
 * is confirmed against an official fixture.</p>
 */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
@ConditionalOnBean(PrroDocumentSigner.class)
public class PrroSigningService {

    private final PrroOperationService operations;
    private final PrroDocumentSigner signer;

    public PrroSigningService(PrroOperationService operations, PrroDocumentSigner signer) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    /**
     * Signs the caller-supplied protocol signing input for an operation in
     * PREPARED state. The adapter must treat the operation's signerKeyId as
     * the required identity and report the ID derived from the actual key used.
     *
     * <p>The input is not modified by this method. Its XML/MAC/previous-document
     * hash contents must be prepared according to the validated DPS contract
     * before a concrete signer adapter is connected.</p>
     */
    public SigningResult signPreparedDocument(long operationId, byte[] signingInput) {
        if (signingInput == null || signingInput.length == 0) {
            throw new IllegalArgumentException("PRRO signing input must not be empty");
        }

        PrroOperationService.Operation operation = operations.get(operationId);
        if (operation.status() != PrroOperationService.Status.PREPARED) {
            throw new IllegalStateException("Only a PREPARED PRRO operation can be signed");
        }
        String expectedSignerKeyId = operation.signerKeyId();
        if (expectedSignerKeyId == null || expectedSignerKeyId.isBlank()) {
            throw new IllegalStateException("PRRO operation has no bound signer key ID");
        }

        SignedPayload signed = Objects.requireNonNull(
                signer.sign(operation, signingInput.clone()), "signer result");
        if (!expectedSignerKeyId.equals(signed.signerKeyId())) {
            throw new SignerIdentityMismatchException(
                    "Signing adapter used a different key than the one bound to the active PRRO shift");
        }

        byte[] payloadBytes = signed.bytes();
        String payloadHash = sha256(payloadBytes);
        PrroOperationService.Operation signedOperation = operations.transition(
                operationId,
                PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.SIGNED,
                payloadHash,
                null,
                null,
                null);

        return new SigningResult(signedOperation, signed.signerKeyId(), payloadHash, payloadBytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record SigningResult(PrroOperationService.Operation operation, String signerKeyId,
                                String signedPayloadSha256, byte[] signedPayload) {
        public SigningResult {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(signerKeyId, "signerKeyId");
            Objects.requireNonNull(signedPayloadSha256, "signedPayloadSha256");
            if (signedPayload == null || signedPayload.length == 0) {
                throw new IllegalArgumentException("Signed payload must not be empty");
            }
            signedPayload = signedPayload.clone();
        }

        @Override
        public byte[] signedPayload() {
            return signedPayload.clone();
        }
    }

    public static class SignerIdentityMismatchException extends RuntimeException {
        public SignerIdentityMismatchException(String message) {
            super(message);
        }
    }
}
