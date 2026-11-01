package com.example.baget.prro.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Builds the complete unsigned XML envelope for a shift-opening SERVICECHK.
 *
 * <p>The caller must pass the exact previous-check XML bytes returned by DPS
 * (or {@code null} only when DPS confirms that this PRRO has no previous
 * check). The bytes must not be decoded, normalized, or reserialized before
 * hashing.</p>
 *
 * <p>This class does not QES-sign the envelope or submit it to DPS.</p>
 */
public final class PrroShiftOpenEnvelopeBuilder {
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final byte[] XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"windows-1251\"?>".getBytes(WINDOWS_1251);

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
     * Returns the complete canonical XML envelope as Windows-1251 bytes,
     * including the XML declaration expected by the DPS protocol examples.
     *
     * @param document shift-opening DAT fields
     * @param exactPreviousCheckXml exact previous Check XML bytes, or null
     *                              only for the first check ever created on
     *                              this PRRO
     */
    public byte[] build(PrroShiftOpenDatBuilder.ShiftOpenDocument document,
                        byte[] exactPreviousCheckXml) {
        Objects.requireNonNull(document, "document");
        if (exactPreviousCheckXml != null && exactPreviousCheckXml.length == 0) {
            throw new IllegalArgumentException(
                    "Previous Check XML bytes must not be empty; use null only when no previous check exists");
        }

        String mac = exactPreviousCheckXml == null
                ? ""
                : PrroPreviousCheckHash.sha256Hex(exactPreviousCheckXml);
        String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                + "<RQ V=\"1\">"
                + datBuilder.build(document)
                + "<MAC ID=\"\">" + mac + "</MAC>"
                + "</RQ>";

        byte[] canonicalPacket = canonicalizer.canonicalizeToWindows1251(xml);
        ByteArrayOutputStream result = new ByteArrayOutputStream(
                XML_DECLARATION.length + canonicalPacket.length);
        result.writeBytes(XML_DECLARATION);
        result.writeBytes(canonicalPacket);
        return result.toByteArray();
    }
}
