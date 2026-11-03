package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fixed local regression vector for the current canonicalizer and hash helper.
 *
 * <p>This is deliberately not described as an official DPS protocol vector.
 * It protects the byte-level behavior of these two helpers while the exact
 * representation required by the fiscal server is still being verified.</p>
 */
class PrroProtocolByteContractTest {

    private final PrroXmlCanonicalizer canonicalizer = new PrroXmlCanonicalizer();

    @Test
    void canonicalUtf8BytesProduceStablePreviousCheckHash() {
        String xml = """
                <?xml version="1.0" encoding="windows-1251"?>
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
    void hashUsesExactBytesRatherThanReencodingText() {
        byte[] utf8Bytes = canonicalizer.canonicalizeToWindows1251(
                "<DAT FN=\"1\"><NM>Хліб</NM><TS>20261025120000</TS></DAT>");

        assertEquals(
                PrroPreviousCheckHash.sha256Hex(windows1251Bytes),
                PrroPreviousCheckHash.sha256Hex(windows1251Bytes.clone()));
        assertEquals(false, PrroPreviousCheckHash.sha256Hex(windows1251Bytes)
                .equals(PrroPreviousCheckHash.sha256Hex(
                        new String(windows1251Bytes, StandardCharsets.UTF_8)
                                .getBytes(StandardCharsets.UTF_8))));
    }

    private static final class CharsetForTests {
        private static final java.nio.charset.Charset WINDOWS_1251 =
                java.nio.charset.Charset.forName("windows-1251");
    }
}
