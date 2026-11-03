package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrroProtocolByteContractTest {

    private final PrroXmlCanonicalizer canonicalizer = new PrroXmlCanonicalizer();

    @Test
    void canonicalUtf8BytesProduceStablePreviousCheckHash() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DAT FN="1">
                  <NM>Хліб</NM>
                  <TS>20261025120000</TS>
                </DAT>
                """;

        byte[] expectedBytes = "<DAT FN=\"1\"><NM>Хліб</NM><TS>20261025120000</TS></DAT>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = canonicalizer.canonicalizeToUtf8(xml);

        assertArrayEquals(expectedBytes, actualBytes);
        assertEquals(
                "039161147a23a816024ac0b436305d441b6b05eb8cb142fe6608848a8352fd8f",
                PrroPreviousCheckHash.sha256Hex(actualBytes));
    }

    @Test
    void hashUsesExactUtf8BytesRatherThanReencodingWithAnotherCharset() {
        byte[] utf8Bytes = canonicalizer.canonicalizeToUtf8(
                "<DAT FN=\"1\"><NM>Хліб</NM><TS>20261025120000</TS></DAT>");
        byte[] utf8RoundTrip = new String(utf8Bytes, StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8);

        assertEquals(PrroPreviousCheckHash.sha256Hex(utf8Bytes),
                PrroPreviousCheckHash.sha256Hex(utf8RoundTrip));
        assertNotEquals(PrroPreviousCheckHash.sha256Hex(utf8Bytes),
                PrroPreviousCheckHash.sha256Hex(
                        new String(utf8Bytes, StandardCharsets.UTF_8)
                                .getBytes(StandardCharsets.ISO_8859_1)));
    }
}
