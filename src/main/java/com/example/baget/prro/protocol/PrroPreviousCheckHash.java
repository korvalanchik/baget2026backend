package com.example.baget.prro.protocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hexadecimal hash used for the previous-Check link in
 * the Electronic Cabinet gRPC PRRO protocol.
 *
 * <p>The caller must pass the exact previous Check XML byte sequence as
 * persisted/sent. This utility deliberately does not choose whether a caller
 * should use an unsigned, canonical, or QES-signed representation: that input
 * must be the exact representation required by the verified DPS fixture.</p>
 *
 * <p>This utility does not define the first-check MAC/XML-field behavior.
 * Keep the API-level previous-Check hash rule separate from the XML protocol's
 * {@code MAC} field until the selected protocol specification explicitly
 * establishes their relationship.</p>
 */
public final class PrroPreviousCheckHash {

    private PrroPreviousCheckHash() {
    }

    public static String sha256Hex(byte[] exactPreviousCheckXml) {
        if (exactPreviousCheckXml == null || exactPreviousCheckXml.length == 0) {
            throw new IllegalArgumentException("Previous Check XML bytes must not be empty");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(exactPreviousCheckXml));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
