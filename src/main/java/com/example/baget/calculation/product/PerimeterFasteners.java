package com.example.baget.calculation.product;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class PerimeterFasteners {
    private PerimeterFasteners() {}
    public static int count(BigDecimal widthMm, BigDecimal heightMm, int spacingMm) {
        if(widthMm==null || heightMm==null || widthMm.signum()<=0 || heightMm.signum()<=0 || spacingMm<=0)
            throw new IllegalArgumentException("Positive dimensions and spacing required");
        return widthMm.add(heightMm).multiply(BigDecimal.valueOf(2))
            .divide(BigDecimal.valueOf(spacingMm),0,RoundingMode.CEILING).max(BigDecimal.valueOf(4)).intValueExact();
    }
}
