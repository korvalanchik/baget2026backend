package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroXmlCanonicalizerTest {

    private final PrroXmlCanonicalizer canonicalizer = new PrroXmlCanonicalizer();

    @Test
    void canonicalizesGeneralPurposeReceiptUsingDpsAppendixAExample() {
        String xml = """
                <?xml version="1.0" encoding="windows-1251"?>
                <DAT FN="4538765845" TN="ПН 345612052809" ZN="АА57506761" DI="238" V="1">
                  <C T="0">
                    <P N="1" C="120" NM="Хліб" SM="370" TX="1"/>
                    <M N="2" T="0" SM="370"/>
                    <E N="3" NO="19" SM="370" FN="4538765845" TS="20110801112601"
                       TX="1" TXPR="20.00" TXSM="125" DTPR="0.00" DTSM="0" TXTY="0" TXAL="0"/>
                  </C>
                  <TS>20110801112601</TS>
                </DAT>
                """;

        String expected = "<DAT DI=\"238\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"АА57506761\">"
                + "<C T=\"0\">"
                + "<P C=\"120\" N=\"1\" NM=\"Хліб\" SM=\"370\" TX=\"1\"></P>"
                + "<M N=\"2\" SM=\"370\" T=\"0\"></M>"
                + "<E DTPR=\"0.00\" DTSM=\"0\" FN=\"4538765845\" N=\"3\" NO=\"19\" "
                + "SM=\"370\" TS=\"20110801112601\" TX=\"1\" TXAL=\"0\" TXPR=\"20.00\" "
                + "TXSM=\"125\" TXTY=\"0\"></E>"
                + "</C><TS>20110801112601</TS></DAT>";

        assertEquals(expected, canonicalizer.canonicalize(xml));
    }

    @Test
    void normalizesWhitespaceExpandsEmptyElementsAndPreservesAttributeValues() {
        String xml = "<ROOT  z=\"last\" a=\"value with  spaces\">"
                + "\n  <TEXT>   this   is \t tag value   </TEXT>\n"
                + "  <EMPTY b=\"2\" a=\"1\" />\n"
                + "</ROOT>";

        assertEquals(
                "<ROOT a=\"value with  spaces\" z=\"last\">"
                        + "<TEXT> this is tag value </TEXT>"
                        + "<EMPTY a=\"1\" b=\"2\"></EMPTY>"
                        + "</ROOT>",
                canonicalizer.canonicalize(xml));
    }

    @Test
    void canonicalizesAndEncodesPacketAsWindows1251WithoutXmlDeclaration() {
        String xml = """
                <?xml version="1.0" encoding="windows-1251"?>
                <DAT FN="4538765845" TN="ПН 345612052809" ZN="АА57506761" DI="238" V="1">
                  <C T="0">
                    <P N="1" C="120" NM="Хліб" SM="370" TX="1"/>
                  </C>
                  <TS>20110801112601</TS>
                </DAT>
                """;

        String expected = "<DAT DI=\\"238\\" FN=\\"4538765845\\" TN=\\"ПН 345612052809\\" V=\\"1\\" ZN=\\"АА57506761\\">"
                + "<C T=\\"0\\"><P C=\\"120\\" N=\\"1\\" NM=\\"Хліб\\" SM=\\"370\\" TX=\\"1\\"></P>"
                + "</C><TS>20110801112601</TS></DAT>";
        byte[] expectedBytes = expected.getBytes(Charset.forName("windows-1251"));

        assertArrayEquals(expectedBytes, canonicalizer.canonicalizeToWindows1251(xml));
        assertEquals(expected, new String(expectedBytes, Charset.forName("windows-1251")));
    }

    @Test
    void rejectsCharactersNotRepresentableInWindows1251() {
        assertThrows(IllegalArgumentException.class,
                () -> canonicalizer.canonicalizeToWindows1251("<DAT>漢</DAT>"));
    }

    @Test
    void rejectsMalformedXml() {
        assertThrows(IllegalArgumentException.class,
                () -> canonicalizer.canonicalize("<DAT><C></DAT>"));
    }

    @Test
    void rejectsDocumentTypeDeclarationsAndExternalEntities() {
        String xml = """
                <!DOCTYPE DAT [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <DAT>&xxe;</DAT>
                """;

        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(xml));
    }

    @Test
    void rejectsBlankXml() {
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize("  \n"));
    }
}
