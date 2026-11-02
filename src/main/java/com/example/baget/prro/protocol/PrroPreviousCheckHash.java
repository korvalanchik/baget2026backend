package com.example.baget.prro.protocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hexadecimal hash used for the previous-Check link in
 * the Electronic Cabinet gRPC PRRO protocol.
 *
 * <p>The input must be the exact unsigned XML envelope that was persisted as
 * {@code prro_operations.signing_input} and passed to IIT for signing. DPS
 * links the next document to these original signing-input bytes, not to the
 * CMS/PKCS#7 signature container returned in {@code CheckResponse.data_sign}.
 * Do not decode/re-encode, normalize, or serialize the bytes again.</p>
 *
 * <p>This utility only hashes the supplied signing-input bytes. The caller is
 * responsible for obtaining the authoritative previous fiscalized operation
 * and for handling the first-document case.</p>
 */
public final class PrroPreviousCheckHash {

    private PrroPreviousCheckHash() {
    }

    public static String sha256Hex(byte[] exactPreviousSigningInput) {
        if (exactPreviousSigningInput == null || exactPreviousSigningInput.length == 0) {
            throw new IllegalArgumentException("Previous signing-input bytes must not be empty");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(exactPreviousSignedCheckXml));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
