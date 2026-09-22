package com.example.baget.calculation.estimate;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EstimateTotalsTest {
    @Test void sumsFinalAmountsWithTwoDecimalPlaces() {
        assertEquals(new BigDecimal("944.27"), EstimateTotals.sum(List.of(new BigDecimal("666.58"), new BigDecimal("277.69"))));
        assertEquals(new BigDecimal("0.02"), EstimateTotals.sum(List.of(new BigDecimal("0.01"), new BigDecimal("0.01"))));
        assertEquals(new BigDecimal("10.00"), EstimateTotals.sum(List.of(BigDecimal.TEN)));
    }
    @Test void rejectsUnexpectedFractionalKopecksAndNegativeAmounts() {
        assertThrows(ArithmeticException.class, () -> EstimateTotals.sum(List.of(new BigDecimal("0.005"))));
        assertThrows(IllegalArgumentException.class, () -> EstimateTotals.sum(List.of(new BigDecimal("-1.00"))));
    }
}
