package com.example.baget.calculation.suspension;

import java.math.BigDecimal;

/** Pure composition rules. Thresholds use entered mm, without rounding. */
public final class SuspensionRules {
    public static final long DVP = 4460, PVC = 27520, MIRROR = 27760, CORD = 28260, POZZI = 23080;
    public static boolean isRigidBacking(Long partNo) {
        return partNo != null && (partNo == DVP || partNo == PVC);
    }
    public static final long H01 = 27580, H04 = 14450, H099 = 14460, POWER = 29650;
    public enum Mode { AUTO, MANUAL, NONE }
    public record Plan(Long hangerPartNo, int hangersPerProduct, int pozziPerProduct, boolean cordIncluded) {}

    public Plan resolve(BigDecimal widthMm, boolean hasRigidBacking, boolean hasMirror, Mode mode,
                        Long selectedPartNo, Integer hangerQuantity, boolean includePozzi, Integer pozziQuantity) {
        if (widthMm == null || widthMm.signum() <= 0 || mode == null)
            throw new IllegalArgumentException("Потрібні додатна ширина та режим підвісу");
        quantity(hangerQuantity); quantity(pozziQuantity);
        if (mode != Mode.MANUAL && selectedPartNo != null)
            throw new IllegalArgumentException("hangerPartNo дозволено лише в MANUAL");
        if (mode == Mode.NONE && hangerQuantity != null)
            throw new IllegalArgumentException("Для NONE кількість основних підвісів не задається");
        if (!includePozzi && pozziQuantity != null)
            throw new IllegalArgumentException("Кількість лапок задана без увімкнення лапок");

        long automatic = hasMirror ? (gt(widthMm, 400) ? POWER : H099)
                : !hasRigidBacking ? (gt(widthMm, 800) ? POWER : H099)
                : widthMm.compareTo(BigDecimal.valueOf(150)) < 0 ? H01 : H04;
        Long hanger = switch (mode) {
            case AUTO -> automatic;
            case NONE -> null;
            case MANUAL -> {
                if (selectedPartNo == null || (selectedPartNo != H01 && selectedPartNo != H04
                        && selectedPartNo != H099 && selectedPartNo != POWER))
                    throw new IllegalArgumentException("Невідомий або відсутній тип підвісу");
                yield selectedPartNo;
            }
        };
        if (hanger != null) {
            if (crocodile(hanger) && (!hasRigidBacking || hasMirror))
                throw new IllegalArgumentException("Крокодили дозволені лише з ДВП/ПВХ і без дзеркала");
            if ((hasMirror || !hasRigidBacking) && hanger != automatic)
                throw new IllegalArgumentException("Для дзеркала або виробу без ДВП/ПВХ дотримуйтеся порога 400/800 мм");
        }
        int count = hanger == null ? 0 : hangerQuantity != null ? hangerQuantity
                : hanger == H01 ? 1 : hanger == H04 ? (gt(widthMm, 450) ? 2 : 1) : 2;
        if (includePozzi && (!hasRigidBacking || (hanger != null && !crocodile(hanger))))
            throw new IllegalArgumentException("Лапки потребують ДВП/ПВХ та сумісні лише з крокодилами або без підвісів");
        int pozzi = !includePozzi ? 0 : pozziQuantity != null ? pozziQuantity
                : widthMm.compareTo(BigDecimal.valueOf(300)) >= 0 ? 2 : 1;
        boolean cord = hanger != null && !crocodile(hanger) && !(hasMirror && gt(widthMm, 400));
        return new Plan(hanger, count, pozzi, cord);
    }

    /** Null preserves automatic behavior; explicit true cannot bypass compatibility rules. */
    public Plan withCord(Plan automatic, Boolean includeCord) {
        if (includeCord == null) return automatic;
        if (includeCord && !automatic.cordIncluded())
            throw new IllegalArgumentException("Шнур дозволено лише з підвісами 099/силовими, крім дзеркала шириною понад 400 мм");
        return new Plan(automatic.hangerPartNo(), automatic.hangersPerProduct(),
                automatic.pozziPerProduct(), includeCord);
    }

    private static boolean crocodile(long id) { return id == H01 || id == H04; }
    private static boolean gt(BigDecimal width, int boundary) { return width.compareTo(BigDecimal.valueOf(boundary)) > 0; }
    private static void quantity(Integer quantity) {
        if (quantity != null && (quantity < 1 || quantity > 10000))
            throw new IllegalArgumentException("Кількість кріплень на виріб має бути від 1 до 10000");
    }
}
