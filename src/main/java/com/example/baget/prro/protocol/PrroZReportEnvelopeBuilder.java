package com.example.baget.prro.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Builds the complete unsigned RQ envelope for a ZREPORT document.
 * The previous document input must be the exact signed bytes returned by DPS
 * in CheckResponse.data_sign. No decoding or XML reserialization is allowed.
 */
public final class PrroZReportEnvelopeBuilder {
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final byte[] XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"windows-1251\"?>".getBytes(WINDOWS_1251);

    private final PrroXmlCanonicalizer canonicalizer;

    public PrroZReportEnvelopeBuilder() {
        this(new PrroXmlCanonicalizer());
    }

    PrroZReportEnvelopeBuilder(PrroXmlCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public byte[] build(String zReportDatXml, byte[] exactPreviousSignedCheckXml) {
        if (zReportDatXml == null || zReportDatXml.isBlank()
                || !zReportDatXml.startsWith("<DAT>") && !zReportDatXml.startsWith("<DAT ")) {
            throw new IllegalArgumentException("ZREPORT DAT XML must have a DAT root element");
        }
        if (exactPreviousSignedCheckXml == null || exactPreviousSignedCheckXml.length == 0) {
            throw new IllegalArgumentException("An open shift must have a previous signed document");
        }

        String previousHash = PrroPreviousCheckHash.sha256Hex(exactPreviousSignedCheckXml);
        String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                + "<RQ V=\"1\">"
                + zReportDatXml
                + "<MAC ID=\"\">" + previousHash + "</MAC>"
                + "</RQ>";

        byte[] canonicalPacket = canonicalizer.canonicalizeToWindows1251(xml);
        ByteArrayOutputStream result = new ByteArrayOutputStream(
                XML_DECLARATION.length + canonicalPacket.length);
        result.writeBytes(XML_DECLARATION);
        result.writeBytes(canonicalPacket);
        return result.toByteArray();
    }
}
