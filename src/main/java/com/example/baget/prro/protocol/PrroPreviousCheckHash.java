package com.example.baget.prro.protocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hexadecimal hash used for the previous-Check link in
 * the Electronic Cabinet gRPC PRRO protocol.
 *
 * <p>The input must be the exact bytes of the previous QES-signed Check XML
 * document, including its signature and timestamp, as returned by DPS in
 * {@code CheckResponse.data_sign} or as originally sent in
 * {@code Check.check_sign}. Do not hash the unsigned XML, decode/re-encode the
 * payload, normalize XML, or serialize it again.</p>
 *
 * <p>This utility only hashes the supplied signed XML bytes. The caller is
 * responsible for obtaining the authoritative previous document and for
 * handling the first-document case.</p>
 */
public final class PrroPreviousCheckHash {

    private PrroPreviousCheckHash() {
    }

    public static String sha256Hex(byte[] exactPreviousSignedCheckXml) {
        if (exactPreviousSignedCheckXml == null || exactPreviousSignedCheckXml.length == 0) {
            throw new IllegalArgumentException("Previous signed Check XML bytes must not be empty");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(exactPreviousSignedCheckXml));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
