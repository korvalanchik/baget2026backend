package com.example.baget.calculation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Geometry only. All input dimensions and crossbar clear lengths are in mm. */
public final class CrossbarCalculator {
    private static final BigDecimal THRESHOLD_MM = new BigDecimal("1000");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal TWO = new BigDecimal("2");

    public enum Direction { ALONG_HEIGHT, ALONG_WIDTH }
    public record Segment(Direction direction, BigDecimal lengthMm, BigDecimal lengthMeters) {}
    public record Result(List<Segment> segmentsPerProduct, BigDecimal consumptionPerProduct,
                         int productQuantity, BigDecimal totalConsumption) {
        public Result { segmentsPerProduct = List.copyOf(segmentsPerProduct); }
    }

    public Result calculate(BigDecimal widthMm, BigDecimal heightMm,
                            BigDecimal railWidthMm, int productQuantity) {
        positive(widthMm, "widthMm");
        positive(heightMm, "heightMm");
        positive(railWidthMm, "railWidthMm");
        if (productQuantity <= 0) throw new IllegalArgumentException("productQuantity must be positive");
        BigDecimal deduction = railWidthMm.multiply(TWO);
        BigDecimal insideWidth = widthMm.subtract(deduction);
        BigDecimal insideHeight = heightMm.subtract(deduction);
        // Reject impossible geometry instead of pricing negative/zero-length rails.
        positive(insideWidth, "insideWidthMm");
        positive(insideHeight, "insideHeightMm");
        var segments = new ArrayList<Segment>();
        // Use raw dimensions as in the legacy JS threshold/length rule (no ceil).
        if (widthMm.compareTo(THRESHOLD_MM) >= 0) {
            segments.add(new Segment(Direction.ALONG_HEIGHT, insideHeight, insideHeight.divide(THOUSAND)));
        }
        if (heightMm.compareTo(THRESHOLD_MM) >= 0) {
            segments.add(new Segment(Direction.ALONG_WIDTH, insideWidth, insideWidth.divide(THOUSAND)));
        }
        BigDecimal perProduct = segments.stream().map(Segment::lengthMeters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(segments, perProduct, productQuantity,
                perProduct.multiply(BigDecimal.valueOf(productQuantity)));
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
