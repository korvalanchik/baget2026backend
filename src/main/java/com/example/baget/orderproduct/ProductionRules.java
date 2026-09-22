package com.example.baget.orderproduct;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure rules shared by production validation and tests. */
public final class ProductionRules {
    private ProductionRules() {}
    public static BigDecimal amount(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null || quantity.signum() < 0 || price.signum() < 0)
            throw new IllegalArgumentException("Quantity and price must be non-negative");
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
    public static boolean transition(int from, int to, boolean finalized) {
        if (!finalized) return (from == 1 && to == 2) || (from == 2 && to == 3)
                || (from == 3 && to == 2) || ((from == 1 || from == 2 || from == 3) && (to == 5 || to == 10));
        return ((from == 6 || from == 7 || from == 8 || from == 4 || from == 9) && to == 11)
                || (from == 11 && to == 12);
    }
    public static boolean canConsume(BigDecimal stock, BigDecimal otherReservations, BigDecimal actual) {
        return actual.signum() == 0 || (stock != null && stock.subtract(otherReservations).compareTo(actual) >= 0);
    }
}
