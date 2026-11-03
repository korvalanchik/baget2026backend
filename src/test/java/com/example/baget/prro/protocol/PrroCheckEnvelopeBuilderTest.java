package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PrroCheckEnvelopeBuilderTest {
    @Test
    void usesHashOfExactPreviousUnsignedSigningInput() {
        byte[] previousSigningInput = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><RQ><DAT DI=\"1\"/></RQ>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] previousSignedPayload = "CMS signed bytes".getBytes(StandardCharsets.UTF_8);
        String previousHash = PrroPreviousCheckHash.sha256Hex(previousSigningInput);
        String signedHash = PrroPreviousCheckHash.sha256Hex(previousSignedPayload);
        assertNotEquals(previousHash, signedHash);

        String dat = "<DAT FN=\"4000318883\" TN=\"2512702072\" ZN=\"4000318883\" DI=\"2\">"
                + "<C T=\"0\"><P N=\"1\" NM=\"Тестова послуга\" SM=\"100\" TX=\"Н\"></P>"
                + "<M N=\"2\" T=\"0\" NM=\"Готівка\" SM=\"100\"></M>"
                + "<E N=\"3\" NO=\"2\" SM=\"100\" TX=\"Н\"></E></C></DAT>";

        String xml = new String(new PrroCheckEnvelopeBuilder().build(dat, previousSigningInput),
                StandardCharsets.UTF_8);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<RQ V=\"1\">"));
        assertTrue(xml.contains("<MAC ID=\"\"" + ">" + previousHash + "</MAC>"));
        assertFalse(xml.contains(signedHash));
        assertTrue(xml.contains("Тестова послуга"));
        assertTrue(xml.contains("Готівка"));
    }

    @Test
    void rejectsMissingPreviousDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrroCheckEnvelopeBuilder().build("<DAT></DAT>", null));
    }
}
