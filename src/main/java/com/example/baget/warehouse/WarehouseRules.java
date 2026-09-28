package com.example.baget.warehouse;
import java.math.BigDecimal;
public final class WarehouseRules {
    private WarehouseRules() {}
    public static boolean same(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    public static boolean route(String from,String to){return ("WORKSHOP".equals(from)&&"WAREHOUSE".equals(to)) || ("WAREHOUSE".equals(from)&&"BRANCH".equals(to)) || ("BRANCH".equals(from)&&"CUSTOMER".equals(to)) || (("WAREHOUSE".equals(from)||"BRANCH".equals(from))&&"WORKSHOP".equals(to));}
    public static BigDecimal stockAfter(BigDecimal before,String action,BigDecimal qty) {
        if(qty==null || qty.signum()<0) throw new IllegalArgumentException("Некоректна кількість");
        if("COUNT".equals(action)) return qty;
        if(!"RECEIPT".equals(action) || qty.signum()==0) throw new IllegalArgumentException("Вкажіть додатну кількість приймання");
        if(before==null) throw new IllegalArgumentException("Поточний залишок невідомий. Спочатку зафіксуйте його інвентаризацією");
        return before.add(qty);
    }
    public static boolean requestTransition(String from,String to){return ("OPEN".equals(from)&&("ORDERED".equals(to)||"CANCELLED".equals(to))) || ("ORDERED".equals(from)&&("CLOSED".equals(to)||"CANCELLED".equals(to)));}
}
