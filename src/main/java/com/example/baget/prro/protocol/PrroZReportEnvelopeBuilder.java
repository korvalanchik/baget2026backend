package com.example.baget.prro.protocol;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the complete unsigned RQ envelope for a ZREPORT document.
 * The previous document input must be the exact unsigned XML bytes persisted
 * as signing_input before IIT signing. Do not pass CheckResponse.data_sign,
 * which contains the signed payload rather than the original signing input.
 */
@Component
public final class PrroZReportEnvelopeBuilder {
    private static final byte[] XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8);

    private final PrroXmlCanonicalizer canonicalizer;

    public PrroZReportEnvelopeBuilder() {
        this(new PrroXmlCanonicalizer());
    }

    PrroZReportEnvelopeBuilder(PrroXmlCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public byte[] build(String zReportDatXml, byte[] exactPreviousSigningInput) {
        if (zReportDatXml == null || zReportDatXml.isBlank()
                || !zReportDatXml.startsWith("<DAT>") && !zReportDatXml.startsWith("<DAT ")) {
            throw new IllegalArgumentException("ZREPORT DAT XML must have a DAT root element");
        }
        if (exactPreviousSigningInput == null || exactPreviousSigningInput.length == 0) {
            throw new IllegalArgumentException("An open shift must have a previous signed document");
        }

        String previousHash = PrroPreviousCheckHash.sha256Hex(exactPreviousSigningInput);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<RQ V=\"1\">"
                + zReportDatXml
                + "<MAC ID=\"\">" + previousHash + "</MAC>"
                + "</RQ>";

        byte[] canonicalPacket = canonicalizer.canonicalizeToUtf8(xml);
        ByteArrayOutputStream result = new ByteArrayOutputStream(
                XML_DECLARATION.length + canonicalPacket.length);
        result.writeBytes(XML_DECLARATION);
        result.writeBytes(canonicalPacket);
        return result.toByteArray();
    }
}
