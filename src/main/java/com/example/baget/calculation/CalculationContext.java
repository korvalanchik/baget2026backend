package com.example.baget.calculation;

import java.math.BigDecimal;

/** Internal context, not an HTTP request. Dimensions in mm; counts per product. */
public record CalculationContext(
        BigDecimal widthMm,
        BigDecimal heightMm,
        int productQuantity,
        BigDecimal unitsPerProduct
) {
    public CalculationContext {
        if (productQuantity <= 0) {
            throw new IllegalArgumentException("productQuantity must be positive");
        }
    }
}
