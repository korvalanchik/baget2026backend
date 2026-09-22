package com.example.baget.orderproduct;
import java.math.BigDecimal;
public final class ReservationMath {
    private ReservationMath() {}
    public static BigDecimal allocate(BigDecimal stock, BigDecimal alreadyReserved, BigDecimal demand) {
        if (demand.signum() < 0 || alreadyReserved.signum() < 0) throw new IllegalArgumentException("Negative quantity");
        if (stock == null) return BigDecimal.ZERO;
        return stock.subtract(alreadyReserved).max(BigDecimal.ZERO).min(demand);
    }
}
