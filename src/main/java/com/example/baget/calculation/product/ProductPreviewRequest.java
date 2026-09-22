package com.example.baget.calculation.product;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record ProductPreviewRequest(
        @NotNull ProductType productType,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal widthMm,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal heightMm,
        @NotNull @Min(1) @Max(10000) Integer productQuantity,
        @Positive Long framePartNo,
        @Positive Long mirrorPartNo,
        @Positive Long underframePartNo,
        @Positive Long backingPartNo,
        @Valid SuspensionSelection suspension,
        @Positive Long glassPartNo,
        @Min(1) @Max(10000) Integer glassLayers,
        @Min(1) @Max(10000) Integer backingLayers,
        @Positive Long matPartNo,
        @Min(1) @Max(10000) Integer matLayers,
        @Valid MatMargins matMargins,
        @Size(max=50) List<@NotNull @Valid SheetSelection> glass,
        @Size(max=50) List<@NotNull @Valid SheetSelection> backings,
        @Size(max=50) List<@NotNull @Valid SheetSelection> mats
) {
    public enum ProductType { MIRROR_IN_FRAME, UNDERFRAME, FRAMED_ARTWORK }

    // Compatibility with the first step-11 draft; prefer material lists for new clients.
    public ProductPreviewRequest(ProductType type, BigDecimal width, BigDecimal height,
                                 Integer quantity, Long frame, Long mirror, Long underframe,
                                 Long backing, SuspensionSelection suspension, Long glass,
                                 Integer glassLayers, Integer backingLayers, Long mat,
                                 Integer matLayers, MatMargins margins) {
        this(type, width, height, quantity, frame, mirror, underframe, backing, suspension,
                glass, glassLayers, backingLayers, mat, matLayers, margins, null, null, null);
    }

    public ProductPreviewRequest(ProductType type, BigDecimal width, BigDecimal height,
                                 Integer quantity, Long frame, Long mirror, Long underframe,
                                 Long backing, SuspensionSelection suspension) {
        this(type, width, height, quantity, frame, mirror, underframe, backing, suspension,
                null, null, null, null, null, null);
    }

    public ProductPreviewRequest(ProductType type, BigDecimal width, BigDecimal height,
                                 Integer quantity, Long frame, Long mirror, Long underframe) {
        this(type, width, height, quantity, frame, mirror, underframe, null, null);
    }

    // Preserve existing Java callers; mirror HTTP JSON also remains valid.
    public ProductPreviewRequest(ProductType type, BigDecimal width, BigDecimal height,
                                 Integer quantity, Long frame, Long mirror) {
        this(type, width, height, quantity, frame, mirror, null);
    }
}
