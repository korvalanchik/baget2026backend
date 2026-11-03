package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrroZReportAggregatorTest {
    private final PrroZReportAggregator aggregator = new PrroZReportAggregator();

    @Test
    void aggregatesSaleAndReturnIntoTaxPaymentAndCountTotals() {
        byte[] sale = receipt(0, 1000, 1000, "1", "20.00", "100");
        byte[] refund = receipt(1, 500, 500, "1", "20.00", "50");

        PrroZReportAggregator.Aggregates result = aggregator.aggregate(List.of(sale, refund));

        assertEquals(1, result.checkCounts().salesCount());
        assertEquals(1, result.checkCounts().returnCount());
        assertEquals(1, result.taxSummaries().size());
        var tax = result.taxSummaries().get(0);
        assertEquals("1", tax.taxCode());
        assertEquals(new BigDecimal("10.00"), tax.receivedTurnoverUah());
        assertEquals(new BigDecimal("5.00"), tax.paidTurnoverUah());
        assertEquals(new BigDecimal("1.00"), tax.receivedTaxUah());
        assertEquals(new BigDecimal("0.50"), tax.paidTaxUah());
        assertEquals(1, result.paymentTurnovers().size());
        assertEquals(new BigDecimal("10.00"), result.paymentTurnovers().get(0).receivedUah());
        assertEquals(new BigDecimal("5.00"), result.paymentTurnovers().get(0).paidUah());
        assertTrue(result.cashMovements().isEmpty());
    }

    @Test
    void aggregatesTaxFreeTestCheckWithCodeNAndCash() {
        byte[] testCheck = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<RQ V=\"1\"><DAT FN=\"4000318883\"><C T=\"0\">"
                + "<P N=\"1\" NM=\"Тестова послуга\" SM=\"100\" TX=\"Н\"></P>"
                + "<M N=\"2\" T=\"0\" NM=\"Готівка\" SM=\"100\"></M>"
                + "<E N=\"3\" NO=\"1\" SM=\"100\" TX=\"Н\"></E>"
                + "</C></DAT><MAC ID=\"\"></MAC></RQ>").getBytes(StandardCharsets.UTF_8);

        var result = aggregator.aggregate(List.of(testCheck));

        assertEquals(1, result.checkCounts().salesCount());
        assertEquals("Н", result.taxSummaries().get(0).taxCode());
        assertEquals(new BigDecimal("1.00"), result.taxSummaries().get(0).receivedTurnoverUah());
        assertEquals(new BigDecimal("1.00"), result.paymentTurnovers().get(0).receivedUah());
    }

    @Test
    void rejectsDiscountRatherThanGuessingTaxAllocation() {
        byte[] discounted = ("<RQ><DAT><C T=\"0\">"
                + "<P N=\"1\" SM=\"1000\" TX=\"1\"></P>"
                + "<D N=\"2\" SM=\"100\"></D>"
                + "<M N=\"3\" T=\"0\" NM=\"Готівка\" SM=\"900\"></M>"
                + "<E N=\"4\" SM=\"900\" TX=\"1\"></E>"
                + "</C></DAT></RQ>").getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> aggregator.aggregate(List.of(discounted)));
        assertTrue(error.getMessage().contains("unsupported D"));
    }

    private static byte[] receipt(int type, int itemMinor, int paymentMinor,
                                  String taxCode, String rate, String taxMinor) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<RQ V=\"1\"><DAT><C T=\"" + type + "\">"
                + "<P N=\"1\" NM=\"Товар\" SM=\"" + itemMinor + "\" TX=\"" + taxCode + "\"></P>"
                + "<M N=\"2\" T=\"0\" NM=\"Готівка\" SM=\"" + paymentMinor + "\"></M>"
                + "<E N=\"3\" NO=\"1\" SM=\"" + itemMinor + "\" TX=\"" + taxCode
                + "\" TXPR=\"" + rate + "\" TXSM=\"" + taxMinor
                + "\" DTPR=\"0.00\" DTSM=\"0\" TXTY=\"0\" TXAL=\"0\"></E>"
                + "</C></DAT><MAC ID=\"\"></MAC></RQ>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
