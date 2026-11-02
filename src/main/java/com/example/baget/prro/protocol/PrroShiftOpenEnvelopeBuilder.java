package com.example.baget.prro.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Builds the complete unsigned XML envelope for a shift-opening SERVICECHK.
 *
 * <p>The caller must pass the exact bytes of the previous QES-signed Check
 * XML document, including its signature and timestamp, as returned by DPS or
 * originally sent in {@code Check.check_sign}. Pass {@code null} only when
 * DPS confirms that no previous Check exists. Never pass unsigned XML or
 * decode, normalize, or reserialize the signed payload before hashing.</p>
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
     * @param exactPreviousSignedCheckXml exact previous signed Check XML bytes,
     *                                     or null only for the first Check
     */
    public byte[] build(PrroShiftOpenDatBuilder.ShiftOpenDocument document,
                        byte[] exactPreviousSignedCheckXml) {
        Objects.requireNonNull(document, "document");
        if (exactPreviousSignedCheckXml != null && exactPreviousSignedCheckXml.length == 0) {
            throw new IllegalArgumentException(
                    "Previous signed Check XML bytes must not be empty; use null only when no previous check exists");
        }

        String mac = exactPreviousSignedCheckXml == null
                ? ""
                : PrroPreviousCheckHash.sha256Hex(exactPreviousSignedCheckXml);
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
