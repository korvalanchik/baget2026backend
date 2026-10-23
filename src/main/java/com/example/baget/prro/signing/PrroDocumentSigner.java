package com.example.baget.prro.signing;

import com.example.baget.prro.service.PrroOperationService;

/**
 * Adapter boundary for a PRRO signing implementation.
 *
 * <p>The concrete implementation must follow the verified DPS signing
 * contract and return the exact bytes to place in Check.check_sign. It must
 * derive signerKeyId from the certificate/key actually used to sign; it must
 * not simply echo the requested operation identity.</p>
 *
 * <p>This interface intentionally does not prescribe a MAC algorithm, the
 * previous-document hash encoding, a CMS/CAdES/XMLDSig container, or the
 * order of those operations. Those details must be confirmed from an official
 * DPS fixture/specification before a concrete adapter is implemented.</p>
 */
public interface PrroDocumentSigner {

    SignedPayload sign(PrroOperationService.Operation operation, byte[] signingInput);

    /**
     * Exact payload bytes produced by the signer and the public key identifier
     * derived from the signing identity. Payload bytes are defensively copied.
     */
    record SignedPayload(byte[] bytes, String signerKeyId) {
        public SignedPayload {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Signed payload must not be empty");
            }
            bytes = bytes.clone();
            if (signerKeyId == null || signerKeyId.isBlank()) {
                throw new IllegalArgumentException("Signer key ID must not be blank");
            }
            signerKeyId = signerKeyId.trim();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
