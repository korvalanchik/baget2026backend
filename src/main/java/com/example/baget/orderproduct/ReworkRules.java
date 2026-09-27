package com.example.baget.orderproduct;

import java.math.BigDecimal;

/** Rework quantities are additional quantities, never replacements for posted consumption. */
public final class ReworkRules {
    private ReworkRules() {}
    public enum ChargeMode { WORKSHOP, CUSTOMER }
    public static BigDecimal charge(ChargeMode mode, BigDecimal quantity, BigDecimal price) {
        var amount = ProductionRules.amount(quantity, price);
        if (mode == null) throw new IllegalArgumentException("Charge mode required");
        return mode == ChargeMode.CUSTOMER ? amount : BigDecimal.ZERO.setScale(2);
    }
    public static void quantity(String unit, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("999999999")) > 0
                || quantity.stripTrailingZeros().scale() > 6
                || ("PIECE".equals(unit) && quantity.stripTrailingZeros().scale() > 0))
            throw new IllegalArgumentException("Вкажіть додатну додаткову витрату; штучні позиції — цілим числом");
    }
    public static boolean canOpen(boolean confirmed, String state, String location, String pending,
                                  boolean invoiced, boolean alreadyOpen) {
        return confirmed && "COMPLETED".equals(state) && "WORKSHOP".equals(location)
                && pending == null && !invoiced && !alreadyOpen;
    }
}
