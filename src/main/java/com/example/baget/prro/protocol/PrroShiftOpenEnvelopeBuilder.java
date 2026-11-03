package com.example.baget.prro.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the complete unsigned XML envelope for a shift-opening SERVICECHK.
 *
 * <p>The caller must pass the exact unsigned XML bytes persisted as
 * {@code prro_operations.signing_input} for the previous fiscalized document.
 * Pass {@code null} only when DPS confirms that no previous Check exists.
 * Never pass the CMS signature container from {@code data_sign}, or decode,
 * normalize, or reserialize the signing input before hashing.</p>
 *
 * <p>This class does not QES-sign the envelope or submit it to DPS.</p>
 */
public final class PrroShiftOpenEnvelopeBuilder {
    private static final byte[] XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8);

    private final PrroShiftOpenDatBuilder datBuilder;
    private final PrroXmlCanonicalizer canonicalizer;

    public PrroShiftOpenEnvelopeBuilder() {
        this(new PrroShiftOpenDatBuilder(), new PrroXmlCanonicalizer());
    }

    PrroShiftOpenEnvelopeBuilder(PrroShiftOpenDatBuilder datBuilder,
                                 PrroXmlCanonicalizer canonicalizer) {
        this.datBuilder = Objects.requireNonNull(datBuilder, "datBuilder");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    /**
     * Returns the complete canonical XML envelope as UTF-8 bytes,
     * including the XML declaration expected by the DPS protocol examples.
     *
     * @param document shift-opening DAT fields
     * @param exactPreviousSigningInput exact previous unsigned XML signing-input bytes,
     *                                     or null only for the first Check
     */
    public byte[] build(PrroShiftOpenDatBuilder.ShiftOpenDocument document,
                        byte[] exactPreviousSigningInput) {
        Objects.requireNonNull(document, "document");
        if (exactPreviousSigningInput != null && exactPreviousSigningInput.length == 0) {
            throw new IllegalArgumentException(
                    "Previous signed Check XML bytes must not be empty; use null only when no previous check exists");
        }

        String mac = exactPreviousSigningInput == null
                ? ""
                : PrroPreviousCheckHash.sha256Hex(exactPreviousSigningInput);
        String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                + "<RQ V=\"1\">"
                + datBuilder.build(document)
                + "<MAC ID=\"\">" + mac + "</MAC>"
                + "</RQ>";

        byte[] canonicalPacket = canonicalizer.canonicalizeToUtf8(xml);
        ByteArrayOutputStream result = new ByteArrayOutputStream(
                XML_DECLARATION.length + canonicalPacket.length);
        result.writeBytes(XML_DECLARATION);
        result.writeBytes(canonicalPacket);
        return result.toByteArray();
    }
}
