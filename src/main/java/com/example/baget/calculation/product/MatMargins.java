package com.example.baget.calculation.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Visible borders in mm; not the material's technological cutting allowance. */
public record MatMargins(
        @NotNull @PositiveOrZero @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal leftMm,
        @NotNull @PositiveOrZero @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal rightMm,
        @NotNull @PositiveOrZero @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal topMm,
        @NotNull @PositiveOrZero @DecimalMax("100000") @Digits(integer=6, fraction=3) BigDecimal bottomMm
) {}
