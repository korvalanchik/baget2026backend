package com.example.baget.calculation.mounting;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.example.baget.calculation.product.SuspensionSelection;
import java.math.BigDecimal;

public record MountingPreviewRequest(
        @NotNull MountingType mountingType,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal widthMm,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal heightMm,
        @NotNull @Min(1) @Max(10000) Integer productQuantity,
        CanvasType canvasType,
        @Positive Long canvasPartNo,
        @Positive Long underframePartNo,
        @Valid SuspensionSelection suspension
) {
    public MountingPreviewRequest(MountingType type,BigDecimal width,BigDecimal height,Integer quantity,CanvasType canvas,Long canvasPart,Long underframe){this(type,width,height,quantity,canvas,canvasPart,underframe,null);}
    public enum MountingType { NONE, PLANE, UNDERFRAME }
    public enum CanvasType { BLANK, WITH_IMAGE, PRINT, EMBROIDERY }
}
