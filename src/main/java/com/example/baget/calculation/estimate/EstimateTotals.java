package com.example.baget.calculation.estimate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Sum the final amounts of independent products; do not round their raw material amounts again. */
public final class EstimateTotals {
    private EstimateTotals() {}

    public static BigDecimal sum(List<BigDecimal> itemTotals) {
        BigDecimal result = new BigDecimal("0.00");
        for (BigDecimal amount : itemTotals) {
            if (amount == null || amount.signum() < 0)
                throw new IllegalArgumentException("Item total must be non-negative");
            result = result.add(amount.setScale(2, RoundingMode.UNNECESSARY));
        }
        return result;
    }
}
