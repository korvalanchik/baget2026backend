package com.example.baget.calculation.mounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import static com.example.baget.calculation.mounting.MountingPreviewRequest.*;

/** Pure rules. All dimensions and margins are millimetres. */
public final class MountingRules {
    private MountingRules() {}

    public static void validate(MountingPreviewRequest r) {
        if (r == null || r.mountingType() == null || r.widthMm() == null || r.heightMm() == null
                || r.widthMm().signum() <= 0 || r.heightMm().signum() <= 0
                || r.productQuantity() == null || r.productQuantity() <= 0)
            throw new IllegalArgumentException("Потрібні спосіб натяжки, додатні розміри та кількість");
        boolean valid = switch (r.mountingType()) {
            case PLANE -> r.underframePartNo() == null && r.canvasPartNo() == null && r.canvasType() == null;
            case UNDERFRAME -> r.underframePartNo() != null && r.canvasType() != null;
            case NONE -> r.underframePartNo() == null && r.canvasPartNo() != null && r.canvasType() != null;
        };
        if (!valid) throw new IllegalArgumentException("Поля полотна та підрамника не відповідають способу натяжки");
    }

    public static long workPartNo(CanvasType type) {
        if (type == null) throw new IllegalArgumentException("Не задано тип полотна");
        return type == CanvasType.BLANK ? 5150L : 5140L;
    }

    public static BigDecimal canvasArea(MountingPreviewRequest r) {
        validate(r);
        if (r.mountingType() == MountingType.PLANE)
            throw new IllegalArgumentException("Натяжка на площину не додає полотно");
        int margin = r.mountingType() == MountingType.UNDERFRAME
                ? (r.canvasType() == CanvasType.PRINT ? 100 : 80) : 0;
        return r.widthMm().setScale(0, RoundingMode.CEILING).add(BigDecimal.valueOf(margin))
                .multiply(r.heightMm().setScale(0, RoundingMode.CEILING).add(BigDecimal.valueOf(margin)))
                .divide(BigDecimal.valueOf(1_000_000)).setScale(6, RoundingMode.HALF_UP);
    }
}
