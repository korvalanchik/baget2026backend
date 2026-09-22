package com.example.baget.calculation.estimate;

import com.example.baget.calculation.mounting.MountingPreviewResponse;
import com.example.baget.calculation.product.ProductPreviewResponse;
import java.math.BigDecimal;
import java.util.List;

public record EstimatePreviewResponse(List<Item> items, BigDecimal total, String currency) {
    public EstimatePreviewResponse { items = List.copyOf(items); }

    public record Item(String clientItemId, EstimatePreviewRequest.Kind kind, BigDecimal total,
                       ProductPreviewResponse product, MountingPreviewResponse mounting) {}
}
