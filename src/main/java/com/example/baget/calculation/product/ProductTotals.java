package com.example.baget.calculation.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Round the sum once, like legacy resultCostBaget; expose any display difference. */
public record ProductTotals(BigDecimal linesSubtotal, BigDecimal roundingAdjustment, BigDecimal total) {
    public static ProductTotals fromUnroundedAmounts(List<BigDecimal> amounts) {
        BigDecimal raw = BigDecimal.ZERO;
        BigDecimal displayed = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            raw = raw.add(amount);
            displayed = displayed.add(amount.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal total = raw.setScale(2, RoundingMode.HALF_UP);
        return new ProductTotals(displayed.setScale(2), total.subtract(displayed).setScale(2), total);
    }
}
