package com.example.baget.procurement;

import java.math.BigDecimal;

public final class ProcurementRules {
    private ProcurementRules() {}
    public record Need(BigDecimal free, BigDecimal shortage, BigDecimal projectedFree,
                       boolean low, BigDecimal recommended) {}
    /** Demand contains all remaining demand, including its reserved portion. Never subtract reserves twice. */
    public static Need need(BigDecimal stock, BigDecimal demand, BigDecimal reserved,
                            BigDecimal incoming, BigDecimal minimum, BigDecimal target) {
        BigDecimal shortage=demand.subtract(reserved).max(BigDecimal.ZERO);
        if(stock==null) return new Need(null,shortage,null,false,null);
        BigDecimal projected=stock.add(incoming).subtract(demand);
        boolean low=projected.compareTo(minimum)<0;
        return new Need(stock.subtract(reserved),shortage,projected,low,
            projected.signum()<0 || low ? target.subtract(projected).max(BigDecimal.ZERO) : BigDecimal.ZERO);
    }
    public static void quantity(String unit,BigDecimal value,boolean zeroAllowed) {
        if(value==null || value.signum()<0 || (!zeroAllowed && value.signum()==0)
            || value.compareTo(new BigDecimal("999999999"))>0 || value.stripTrailingZeros().scale()>6
            || !java.util.Set.of("METER","SQUARE_METER","PIECE").contains(unit==null?"":unit)
            || ("PIECE".equals(unit) && value.stripTrailingZeros().scale()>0))
            throw new IllegalArgumentException("Перевірте кількість і одиницю матеріалу; штуки мають бути цілими");
    }
    public static void receive(String status,BigDecimal ordered,BigDecimal received,BigDecimal amount) {
        if(!java.util.Set.of("ORDERED","PARTIAL").contains(status))
            throw new IllegalArgumentException("Прихід доступний лише за відкритим замовленням постачальнику");
        if(amount==null || amount.signum()<=0 || amount.compareTo(ordered.subtract(received))>0)
            throw new IllegalArgumentException("Кількість приходу перевищує непоставлений залишок або не є додатною");
    }
}
