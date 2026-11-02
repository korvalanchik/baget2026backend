package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PrroZReportEnvelopeBuilderTest {
    @Test
    void wrapsZReportAndHashesExactPreviousSignedDocumentBytes() {
        byte[] previous = ("<?xml version=\"1.0\"?><RQ><DAT><C T=\"108\"></C></DAT>"
                + "<Signature>signed</Signature></RQ>").getBytes(StandardCharsets.UTF_8);
        String previousHash = PrroPreviousCheckHash.sha256Hex(previous);
        String dat = "<DAT FN=\"4000318883\" TN=\"2512702072\" ZN=\"\" DI=\"2\" V=\"1\">"
                + "<Z NO=\"1\"><M NM=\"Готівка\" SMI=\"0\" SMO=\"0\" T=\"0\"></M>"
                + "<NC NI=\"0\" NO=\"0\"></NC>"
                + "<IO NM=\"Готівка\" SMI=\"0\" SMO=\"0\" T=\"0\"></IO>"
                + "</Z><TS>20261102220000</TS></DAT>";

        byte[] bytes = new PrroZReportEnvelopeBuilder().build(dat, previous);
        String xml = new String(bytes, Charset.forName("windows-1251"));

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"windows-1251\"?>"));
        assertTrue(xml.contains("<RQ V=\"1\">"));
        assertTrue(xml.contains("<MAC ID=\"\">" + previousHash + "</MAC>"));
        assertTrue(xml.contains("<Z NO=\"1\">"));
        assertTrue(xml.contains("<NC NI=\"0\" NO=\"0\"></NC>"));
    }

    @Test
    void rejectsMissingPreviousDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrroZReportEnvelopeBuilder().build("<DAT></DAT>", null));
    }
}
