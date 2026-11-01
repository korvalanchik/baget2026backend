package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrroShiftOpenDatBuilderTest {
    private final PrroShiftOpenDatBuilder builder = new PrroShiftOpenDatBuilder();
    private final PrroXmlCanonicalizer canonicalizer = new PrroXmlCanonicalizer();

    @Test
    void buildsShiftOpenDatUsingServiceDocumentType108() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "4538765845", "ПН 345612052809", 238,
                LocalDateTime.of(2026, 11, 1, 9, 5, 7));

        assertEquals(
                "<DAT DI=\"238\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"\">"
                        + "<C T=\"108\"></C><TS>20261101090507</TS></DAT>",
                canonicalizer.canonicalize(builder.build(document)));
    }

    @Test
    void escapesFiscalAndTaxpayerValuesAsXmlAttributes() {
        var document = new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                "45&38765845", "ПН <3456>", 1,
                LocalDateTime.of(2026, 11, 1, 9, 5));
        String canonical = canonicalizer.canonicalize(builder.build(document));
        assertTrue(canonical.contains("FN=\"45&amp;38765845\""));
        // '>' is valid unescaped in an XML attribute; only '<' must be escaped.
        assertTrue(canonical.contains("TN=\"ПН &lt;3456>\""));
    }

    @Test
    void rejectsInvalidShiftOpenHeader() {
        assertThrows(IllegalArgumentException.class, () ->
                new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                        " ", "TN", 1, LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () ->
                new PrroShiftOpenDatBuilder.ShiftOpenDocument(
                        "FN", "TN", -1, LocalDateTime.now()));
    }
}
