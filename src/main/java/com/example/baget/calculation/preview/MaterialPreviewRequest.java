package com.example.baget.calculation.preview;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MaterialPreviewRequest(
        @NotNull @Positive Long partNo,
        @Positive @DecimalMax("100000") @Digits(integer = 6, fraction = 3) BigDecimal widthMm,
        @Positive @DecimalMax("100000") @Digits(integer = 6, fraction = 3) BigDecimal heightMm,
        @NotNull @Min(1) @Max(10000) Integer productQuantity,
        @Min(1) @Max(10000) Integer unitsPerProduct
) {}
