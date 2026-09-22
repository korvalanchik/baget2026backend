package com.example.baget.calculation.product;

import com.example.baget.calculation.suspension.SuspensionRules;
import jakarta.validation.constraints.*;

/** Only editable mounting choices. Geometry and material context come from the product itself. */
public record SuspensionSelection(
        @NotNull SuspensionRules.Mode hangerMode,
        @Positive Long hangerPartNo,
        @Min(1) @Max(10000) Integer hangersPerProduct,
        @NotNull Boolean includePozzi,
        @Min(1) @Max(10000) Integer pozziPerProduct,
        Boolean includeCord
) {
    public SuspensionSelection(SuspensionRules.Mode hangerMode, Long hangerPartNo,
            Integer hangersPerProduct, Boolean includePozzi, Integer pozziPerProduct) {
        this(hangerMode, hangerPartNo, hangersPerProduct, includePozzi, pozziPerProduct, null);
    }
}
