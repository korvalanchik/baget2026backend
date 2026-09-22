package com.example.baget.calculation.product;

import jakarta.validation.constraints.*;

/** A selected sheet material and the number of identical layers in one product. */
public record SheetSelection(
        @NotNull @Positive Long partNo,
        @NotNull @Min(1) @Max(10000) Integer quantityPerProduct
) {}
