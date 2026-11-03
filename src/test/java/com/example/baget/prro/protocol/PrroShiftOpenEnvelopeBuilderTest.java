package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroShiftOpenEnvelopeBuilderTest {
    private final PrroShiftOpenEnvelopeBuilder builder = new PrroShiftOpenEnvelopeBuilder();

    @Test
    void buildsFirstPrroCheckWithEmptyMac() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 238,
                LocalDateTime.of(2026, 11, 1, 9, 5, 7));

        String actual = new String(builder.build(document, null), StandardCharsets.UTF_8);

        assertEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<RQ V=\"1\">"
                        + "<DAT DI=\"238\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"\">"
                        + "<C T=\"108\"></C><TS>20261101090507</TS></DAT>"
                        + "<MAC ID=\"\"></MAC></RQ>",
                actual);
    }

    @Test
    void regressionUsesPreviousUnsignedSigningInputHashInsteadOfSignedPayloadHash() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 239,
                LocalDateTime.of(2026, 11, 1, 9, 6, 7));

        byte[] previousSigningInput = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<RQ V=\"1\"><DAT DI=\"238\"><C T=\"108\"></C></DAT>"
                + "<MAC ID=\"\"></MAC></RQ>").getBytes(StandardCharsets.UTF_8);
        byte[] previousSignedPayload = "CMS/PKCS7 signed payload fixture".getBytes(StandardCharsets.UTF_8);
        String previousHash = PrroPreviousCheckHash.sha256Hex(previousSigningInput);
        String signedPayloadHash = PrroPreviousCheckHash.sha256Hex(previousSignedPayload);
        String actual = new String(builder.build(document, previousSigningInput), StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains("<MAC ID=\"\">" + previousHash + "</MAC>"));
        org.junit.jupiter.api.Assertions.assertFalse(
                actual.contains("<MAC ID=\"\">" + signedPayloadHash + "</MAC>"));
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
