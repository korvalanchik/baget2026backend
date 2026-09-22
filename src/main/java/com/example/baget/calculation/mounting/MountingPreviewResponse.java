package com.example.baget.calculation.mounting;

import com.example.baget.calculation.product.ProductPreviewResponse.Line;
import java.math.BigDecimal;
import java.util.List;

public record MountingPreviewResponse(
        MountingPreviewRequest.MountingType mountingType,
        MountingPreviewRequest.CanvasType canvasType,
        int productQuantity, List<Line> lines,
        BigDecimal linesSubtotal, BigDecimal roundingAdjustment, BigDecimal total, String currency
) {
    public MountingPreviewResponse { lines = List.copyOf(lines); }
}
