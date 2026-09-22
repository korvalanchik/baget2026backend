package com.example.baget.calculation.suspension;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SuspensionPreviewRequest(
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal widthMm,
        @NotNull @Min(1) @Max(10000) Integer productQuantity,
        @Positive Long backingPartNo,
        @Positive Long mirrorPartNo,
        @NotNull SuspensionRules.Mode hangerMode,
        @Positive Long hangerPartNo,
        @Min(1) @Max(10000) Integer hangersPerProduct,
        @NotNull Boolean includePozzi,
        @Min(1) @Max(10000) Integer pozziPerProduct,
        Boolean includeCord
) {
    public SuspensionPreviewRequest(BigDecimal widthMm, Integer productQuantity,
            Long backingPartNo, Long mirrorPartNo, SuspensionRules.Mode hangerMode,
            Long hangerPartNo, Integer hangersPerProduct, Boolean includePozzi, Integer pozziPerProduct) {
        this(widthMm, productQuantity, backingPartNo, mirrorPartNo, hangerMode,
                hangerPartNo, hangersPerProduct, includePozzi, pozziPerProduct, null);
    }
}
