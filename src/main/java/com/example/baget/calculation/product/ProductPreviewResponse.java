package com.example.baget.calculation.product;

import com.example.baget.calculation.CrossbarCalculator;
import com.example.baget.calculation.preview.MaterialPreviewResponse;
import java.math.BigDecimal;
import java.util.List;

public record ProductPreviewResponse(
        ProductPreviewRequest.ProductType productType,
        int productQuantity, List<Line> lines,
        BigDecimal linesSubtotal, BigDecimal roundingAdjustment, BigDecimal total, String currency
) {
    public record Line(String role, String source, MaterialPreviewResponse calculation,
                       List<CrossbarCalculator.Segment> segmentsPerProduct) {
        public Line { segmentsPerProduct = List.copyOf(segmentsPerProduct); }
        public Line(String role, String source, MaterialPreviewResponse calculation) {
            this(role, source, calculation, List.of());
        }
    }
}
