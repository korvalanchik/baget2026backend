package com.example.baget.prro.protocol;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the complete unsigned RQ envelope for a CHK document.
 * The previous document input must be the exact unsigned XML bytes persisted
 * as signing_input before IIT signing.
 */
@Component
public final class PrroCheckEnvelopeBuilder {
    private static final byte[] XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8);

    private final PrroXmlCanonicalizer canonicalizer;

    public PrroCheckEnvelopeBuilder() {
        this(new PrroXmlCanonicalizer());
    }

    PrroCheckEnvelopeBuilder(PrroXmlCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public byte[] build(String checkDatXml, byte[] exactPreviousSigningInput) {
        if (checkDatXml == null || checkDatXml.isBlank()
                || !checkDatXml.startsWith("<DAT>") && !checkDatXml.startsWith("<DAT ")) {
            throw new IllegalArgumentException("CHK DAT XML must have a DAT root element");
        }
        if (exactPreviousSigningInput == null || exactPreviousSigningInput.length == 0) {
            throw new IllegalArgumentException("An active shift must have a previous fiscalized document");
        }

        String previousHash = PrroPreviousCheckHash.sha256Hex(exactPreviousSigningInput);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<RQ V=\"1\">"
                + checkDatXml
                + "<MAC ID=\"\" >".replace(" >", ">") + previousHash + "</MAC>"
                + "</RQ>";

        byte[] canonicalPacket = canonicalizer.canonicalizeToUtf8(xml);
        ByteArrayOutputStream result = new ByteArrayOutputStream(
                XML_DECLARATION.length + canonicalPacket.length);
        result.writeBytes(XML_DECLARATION);
        result.writeBytes(canonicalPacket);
        return result.toByteArray();
    }
}
