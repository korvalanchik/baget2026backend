package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrroXmlDocumentBuilderTest {

    private final PrroXmlDocumentBuilder builder = new PrroXmlDocumentBuilder();
    private final PrroXmlCanonicalizer canonicalizer = new PrroXmlCanonicalizer();

    @Test
    void buildsGeneralPurposeSaleCheckWithProtocolUnitsAndEscapedXml() {
        var header = header("4538765845", "ПН 345612052809", "АА57506761", 238);
        var document = new PrroXmlDocumentBuilder.CheckDocument(
                header,
                PrroXmlDocumentBuilder.ReceiptType.SALE,
                List.of(new PrroXmlDocumentBuilder.SaleItem(
                        1, "bread-1", "Хліб & булочка", new BigDecimal("10.00"),
                        null, null, "1", null)),
                List.of(new PrroXmlDocumentBuilder.Payment(
                        2, PrroXmlDocumentBuilder.PaymentType.CASH, null, new BigDecimal("10.00"))),
                new PrroXmlDocumentBuilder.ReceiptClosing(
                        3, 19, new BigDecimal("10.00"), new BigDecimal("8.33"), 1,
                        List.of(new PrroXmlDocumentBuilder.TaxBreakdown(
                                "1", new BigDecimal("20.00"), new BigDecimal("1.67"),
                                new BigDecimal("0.00"), BigDecimal.ZERO, 0, 0))));

        String xml = builder.buildCheck(document);
        String canonical = canonicalizer.canonicalize(xml);

        assertEquals(
                "<DAT DI=\"238\" DT=\"0\" FN=\"4538765845\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"АА57506761\">"
                        + "<C T=\"0\">"
                        + "<P C=\"bread-1\" N=\"1\" NM=\"Хліб &amp; булочка\" SM=\"1000\" TX=\"1\"></P>"
                        + "<M N=\"2\" NM=\"Готівка\" SM=\"1000\" T=\"0\"></M>"
                        + "<E CS=\"1\" DTPR=\"0.00\" DTSM=\"0\" FN=\"4538765845\" N=\"3\" NO=\"19\" "
                        + "SE=\"833\" SM=\"1000\" TS=\"20110801112601\" TX=\"1\" TXAL=\"0\" "
                        + "TXPR=\"20.00\" TXSM=\"167\" TXTY=\"0\"></E>"
                        + "</C><TS>20110801112601</TS></DAT>",
                canonical);
        assertTrue(!xml.contains("<MAC"));
    }

    @Test
    void encodesNonDefaultQuantityAndUnitPriceInProtocolUnits() {
        var document = new PrroXmlDocumentBuilder.CheckDocument(
                header("1234567890", "TN", "SERIAL", 4),
                PrroXmlDocumentBuilder.ReceiptType.SALE,
                List.of(new PrroXmlDocumentBuilder.SaleItem(
                        1, "glass", "Скло", new BigDecimal("15.42"),
                        new BigDecimal("1.250"), new BigDecimal("12.34"), "0", null)),
                List.of(new PrroXmlDocumentBuilder.Payment(
                        2, PrroXmlDocumentBuilder.PaymentType.OTHER,
                        "Подарунковий сертифікат", new BigDecimal("15.42"))),
                new PrroXmlDocumentBuilder.ReceiptClosing(
                        3, 1, new BigDecimal("15.42"), null, null, List.of()));

        String xml = builder.buildCheck(document);

        assertTrue(xml.contains("Q=\"1250\""));
        assertTrue(xml.contains("PRC=\"1234\""));
        assertTrue(xml.contains("SM=\"1542\""));
        assertTrue(xml.contains("T=\"1\""));
        assertTrue(xml.contains("NM=\"Подарунковий сертифікат\""));
    }

    @Test
    void buildsZReportWithTaxPaymentAndCashMovementSummaries() {
        var document = new PrroXmlDocumentBuilder.ZReportDocument(
                header("1234567890", "ПН 345612052809", "АА57506761", 415),
                275,
                List.of(new PrroXmlDocumentBuilder.TaxSummary(
                        "1", LocalDate.of(2009, 12, 1), new BigDecimal("20.00"),
                        new BigDecimal("3.14"), new BigDecimal("0.10"),
                        new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                        new BigDecimal("2451.23"), new BigDecimal("60.00"))),
                List.of(new PrroXmlDocumentBuilder.PaymentTurnover(
                        PrroXmlDocumentBuilder.PaymentType.CASH, null,
                        new BigDecimal("2451.23"), new BigDecimal("60.00"))),
                List.of(new PrroXmlDocumentBuilder.CashMovementSummary(
                        PrroXmlDocumentBuilder.PaymentType.CASH, null,
                        new BigDecimal("100.00"), new BigDecimal("2491.23"))),
                new PrroXmlDocumentBuilder.CheckCounts(18, 1));

        assertEquals(
                "<DAT DI=\"415\" DT=\"0\" FN=\"1234567890\" TN=\"ПН 345612052809\" V=\"1\" ZN=\"АА57506761\">"
                        + "<Z NO=\"275\">"
                        + "<TXS DTI=\"0\" DTO=\"0\" DTPR=\"0.00\" SMI=\"245123\" SMO=\"6000\" "
                        + "TS=\"20091201\" TX=\"1\" TXAL=\"0\" TXI=\"314\" TXO=\"10\" TXPR=\"20.00\" "
                        + "TXTY=\"0\"></TXS>"
                        + "<M NM=\"Готівка\" SMI=\"245123\" SMO=\"6000\" T=\"0\"></M>"
                        + "<IO NM=\"Готівка\" SMI=\"10000\" SMO=\"249123\" T=\"0\"></IO>"
                        + "<NC NI=\"18\" NO=\"1\"></NC>"
                        + "</Z><TS>20110801112601</TS></DAT>",
                canonicalizer.canonicalize(builder.buildZReport(document)));
    }

    @Test
    void convertsMoneyAndQuantityWithoutRounding() {
        assertEquals(1234, PrroXmlDocumentBuilder.moneyToMinorUnits(new BigDecimal("12.34")));
        assertEquals(1125, PrroXmlDocumentBuilder.quantityToThousandths(new BigDecimal("1.125")));

        assertThrows(IllegalArgumentException.class,
                () -> PrroXmlDocumentBuilder.moneyToMinorUnits(new BigDecimal("12.341")));
        assertThrows(IllegalArgumentException.class,
                () -> PrroXmlDocumentBuilder.quantityToThousandths(new BigDecimal("1.0001")));
    }

    @Test
    void rejectsClosingOperationBeforeAnItemOrPayment() {
        var document = new PrroXmlDocumentBuilder.CheckDocument(
                header("1234567890", "TN", "SERIAL", 1),
                PrroXmlDocumentBuilder.ReceiptType.SALE,
                List.of(new PrroXmlDocumentBuilder.SaleItem(
                        2, null, "Виріб", new BigDecimal("1.00"),
                        null, null, "0", null)),
                List.of(new PrroXmlDocumentBuilder.Payment(
                        1, PrroXmlDocumentBuilder.PaymentType.CASH, null, new BigDecimal("1.00"))),
                new PrroXmlDocumentBuilder.ReceiptClosing(
                        2, 1, new BigDecimal("1.00"), null, null, List.of()));

        assertThrows(IllegalArgumentException.class, () -> builder.buildCheck(document));
    }

    private static PrroXmlDocumentBuilder.PacketHeader header(
            String fiscalNumber, String taxpayerNumber, String serialNumber, long packetId) {
        return new PrroXmlDocumentBuilder.PacketHeader(
                fiscalNumber, taxpayerNumber, serialNumber, packetId,
                LocalDateTime.of(2011, 8, 1, 11, 26, 1));
    }
}
