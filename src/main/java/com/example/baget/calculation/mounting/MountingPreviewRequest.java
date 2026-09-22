package com.example.baget.calculation.mounting;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MountingPreviewRequest(
        @NotNull MountingType mountingType,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal widthMm,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal heightMm,
        @NotNull @Min(1) @Max(10000) Integer productQuantity,
        CanvasType canvasType,
        @Positive Long canvasPartNo,
        @Positive Long underframePartNo
) {
    public enum MountingType { NONE, PLANE, UNDERFRAME }
    public enum CanvasType { BLANK, WITH_IMAGE, PRINT }
}
