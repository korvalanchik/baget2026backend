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
    void canonicalWindows1251BytesProduceStablePreviousCheckHash() {
        String xml = """
                <?xml version="1.0" encoding="windows-1251"?>
                <DAT FN="1">
                  <NM>Хліб</NM>
                  <TS>20261025120000</TS>
                </DAT>
                """;

        byte[] expectedBytes = "<DAT FN=\"1\"><NM>Хліб</NM><TS>20261025120000</TS></DAT>"
                .getBytes(CharsetForTests.WINDOWS_1251);

        byte[] actualBytes = canonicalizer.canonicalizeToWindows1251(xml);

        assertArrayEquals(expectedBytes, actualBytes);
        assertEquals(
                "5c1754c79b58b7d9b8daf14f42e76f8da7eac4be390ef57816474c07a1f1d7be",
                PrroPreviousCheckHash.sha256Hex(actualBytes));
    }

    @Test
    void hashUsesExactBytesRatherThanReencodingText() {
        byte[] windows1251Bytes = canonicalizer.canonicalizeToWindows1251(
                "<DAT FN=\"1\"><NM>Хліб</NM><TS>20261025120000</TS></DAT>");

        assertEquals(
                PrroPreviousCheckHash.sha256Hex(windows1251Bytes),
                PrroPreviousCheckHash.sha256Hex(windows1251Bytes.clone()));
        assertEquals(false, PrroPreviousCheckHash.sha256Hex(windows1251Bytes)
                .equals(PrroPreviousCheckHash.sha256Hex(
                        new String(windows1251Bytes, CharsetForTests.WINDOWS_1251)
                                .getBytes(StandardCharsets.UTF_8))));
    }

    private static final class CharsetForTests {
        private static final java.nio.charset.Charset WINDOWS_1251 =
                java.nio.charset.Charset.forName("windows-1251");
    }
}
