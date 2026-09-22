package com.example.baget.calculation.suspension;

import com.example.baget.calculation.product.ProductPreviewResponse.Line;
import java.math.BigDecimal;
import java.util.List;

public record SuspensionPreviewResponse(
        SuspensionRules.Plan selection, int productQuantity, List<Line> lines,
        BigDecimal linesSubtotal, BigDecimal roundingAdjustment, BigDecimal total, String currency
) {
    public SuspensionPreviewResponse { lines = List.copyOf(lines); }
}
