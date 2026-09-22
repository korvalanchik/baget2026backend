package com.example.baget.calculation.estimate;

import com.example.baget.calculation.mounting.MountingPreviewRequest;
import com.example.baget.calculation.product.ProductPreviewRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Each item is a separate product/calculation, not another component of the same product. */
public record EstimatePreviewRequest(
        @NotNull @Size(min=1, max=50) List<@NotNull @Valid Item> items
) {
    public enum Kind { PRODUCT, MOUNTING }

    public record Item(
            @NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String clientItemId,
            @NotNull Kind kind,
            @Valid ProductPreviewRequest product,
            @Valid MountingPreviewRequest mounting
    ) {}
}
