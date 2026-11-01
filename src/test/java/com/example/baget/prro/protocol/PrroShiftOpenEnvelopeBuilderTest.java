package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroShiftOpenEnvelopeBuilderTest {
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    private final PrroShiftOpenEnvelopeBuilder builder = new PrroShiftOpenEnvelopeBuilder();

    @Test
    void buildsFirstPrroCheckWithEmptyMac() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 238,
                LocalDateTime.of(2026, 11, 1, 9, 5, 7));

        String actual = new String(builder.build(document, null), WINDOWS_1251);

        assertEquals(
                "<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                        + "<RQ V=\"1\">"
                        + "<DAT DI=\"238\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"\">"
                        + "<C T=\"108\"></C><TS>20261101090507</TS></DAT>"
                        + "<MAC ID=\"\"></MAC></RQ>",
                actual);
    }

    @Test
    void hashesExactPreviousCheckBytesIntoMacAsLowercaseHex() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 239,
                LocalDateTime.of(2026, 11, 1, 9, 6, 7));

        byte[] previous = "abc".getBytes(StandardCharsets.UTF_8);
        String actual = new String(builder.build(document, previous), WINDOWS_1251);

        assertEquals(
                "<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                        + "<RQ V=\"1\">"
                        + "<DAT DI=\"239\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"\">"
                        + "<C T=\"108\"></C><TS>20261101090607</TS></DAT>"
                        + "<MAC ID=\"\">ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad</MAC>"
                        + "</RQ>",
                actual);
    }

    @Test
    void rejectsAnEmptyPreviousCheckInsteadOfSilentlySkippingMac() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 239,
                LocalDateTime.of(2026, 11, 1, 9, 6, 7));

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(document, new byte[0]));
    }
}
