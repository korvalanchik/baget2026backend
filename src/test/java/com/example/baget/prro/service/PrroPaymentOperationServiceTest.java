package com.example.baget.prro.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroPaymentOperationServiceTest {

    @Test
    void allocatesPaymentAcrossProductsAndPreservesExactTotal() {
        List<BigDecimal> amounts = PrroPaymentOperationService.allocateProportionally(
                new BigDecimal("100.00"),
                List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("1")));

        assertEquals(List.of(
                new BigDecimal("25.00"),
                new BigDecimal("50.00"),
                new BigDecimal("25.00")), amounts);
        assertEquals(new BigDecimal("100.00"),
                amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void distributesRoundingRemainderDeterministically() {
        List<BigDecimal> amounts = PrroPaymentOperationService.allocateProportionally(
                new BigDecimal("100.00"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertEquals(List.of(
                new BigDecimal("33.34"),
                new BigDecimal("33.33"),
                new BigDecimal("33.33")), amounts);
    }

    @Test
    void doesNotLoseKopecksWhenPaymentIsSmallerThanNumberOfProducts() {
        List<BigDecimal> amounts = PrroPaymentOperationService.allocateProportionally(
                new BigDecimal("0.02"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertEquals(List.of(
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("0.00")), amounts);
        assertEquals(new BigDecimal("0.02"),
                amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void rejectsAmountsWithMoreThanTwoMeaningfulDecimalPlaces() {
        assertThrows(ArithmeticException.class, () ->
                PrroPaymentOperationService.allocateProportionally(
                        new BigDecimal("1.001"), List.of(BigDecimal.ONE)));
    }

    @Test
    void fallsBackToEqualAllocationWhenAllWeightsAreZero() {
        List<BigDecimal> amounts = PrroPaymentOperationService.allocateProportionally(
                new BigDecimal("12.00"),
                List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(List.of(
                new BigDecimal("4.00"),
                new BigDecimal("4.00"),
                new BigDecimal("4.00")), amounts);
    }
}
