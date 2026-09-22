package com.example.baget.calculation;

import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** No database, prices, inventory writes or HTTP dependencies. */
public final class ConsumptionCalculator {
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal EIGHT = new BigDecimal("8");

    @FunctionalInterface
    private interface Formula {
        BigDecimal calculate(CalculationContext context, BigDecimal profileWidthMeters,
                             Map<String, BigDecimal> params);
    }

    private record Strategy(UnitType unit, Set<String> keys, Formula formula) {}
    private final Map<CalculationMethod, Strategy> strategies;

    public ConsumptionCalculator() {
        var registry = new EnumMap<CalculationMethod, Strategy>(CalculationMethod.class);
        registry.put(CalculationMethod.AREA, new Strategy(UnitType.SQUARE_METER, Set.of(),
                (c, w, p) -> area(c, BigDecimal.ZERO, BigDecimal.ZERO)));
        registry.put(CalculationMethod.AREA_WITH_MARGIN,
                new Strategy(UnitType.SQUARE_METER, Set.of("marginWidthMm", "marginHeightMm"),
                (c, w, p) -> area(c, parameter(p, "marginWidthMm"), parameter(p, "marginHeightMm"))));
        registry.put(CalculationMethod.PERIMETER, new Strategy(UnitType.METER, Set.of(),
                (c, w, p) -> perimeter(c)));
        registry.put(CalculationMethod.FRAME_PROFILE, new Strategy(UnitType.METER, Set.of(),
                (c, w, p) -> {
                    // Legacy PartsService exposes Math.round(ProfilWidth * 1000) mm.
                    BigDecimal profileMm = nonNegative(w, "profileWidthMeters")
                            .multiply(THOUSAND).setScale(0, RoundingMode.HALF_UP);
                    return perimeter(c).add(profileMm.multiply(EIGHT).divide(THOUSAND));
                }));
        registry.put(CalculationMethod.WIDTH_PLUS_ALLOWANCE,
                new Strategy(UnitType.METER, Set.of("allowanceMm"),
                // Old cord formula uses raw width, unlike area/perimeter formulas.
                (c, w, p) -> positive(c.widthMm(), "widthMm")
                        .add(parameter(p, "allowanceMm")).divide(THOUSAND)));
        registry.put(CalculationMethod.QUANTITY, new Strategy(UnitType.PIECE, Set.of(),
                (c, w, p) -> {
                    BigDecimal units = positive(c.unitsPerProduct(), "unitsPerProduct");
                    if (units.stripTrailingZeros().scale() > 0) {
                        throw new IllegalArgumentException("PIECE quantity must be an integer");
                    }
                    return units;
                }));
        // One work/service unit per product, not one per entire order.
        registry.put(CalculationMethod.FIXED, new Strategy(UnitType.PIECE, Set.of(),
                (c, w, p) -> BigDecimal.ONE));
        strategies = Map.copyOf(registry);
    }

    public ConsumptionResult calculate(CalculationMethod method, UnitType unit,
            BigDecimal profileWidthMeters, Map<String, BigDecimal> params,
            CalculationContext context) {
        if (method == null || unit == null || params == null || context == null) {
            throw new IllegalArgumentException("Calculation settings and context are required");
        }
        Strategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
        if (unit != strategy.unit()) {
            throw new IllegalArgumentException("Unit " + unit + " is incompatible with " + method);
        }
        boolean underframeParams = method == CalculationMethod.PERIMETER
                && params.keySet().equals(Set.of("railWidthMm"));
        if (underframeParams) {
            positive(params.get("railWidthMm"), "railWidthMm");
        }
        if (!underframeParams && !params.keySet().equals(strategy.keys())) {
            throw new IllegalArgumentException("Expected parameters for " + method + ": " + strategy.keys());
        }
        BigDecimal perProduct = strategy.formula().calculate(context, profileWidthMeters, params);
        BigDecimal total = perProduct.multiply(BigDecimal.valueOf(context.productQuantity()));
        return new ConsumptionResult(perProduct, context.productQuantity(), total, unit);
    }

    private static BigDecimal area(CalculationContext c, BigDecimal extraWidth, BigDecimal extraHeight) {
        // Margins are TOTAL additions to dimensions, not additions on each side.
        return dimension(c.widthMm(), "widthMm").add(extraWidth)
                .multiply(dimension(c.heightMm(), "heightMm").add(extraHeight))
                .divide(MILLION).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal perimeter(CalculationContext c) {
        return dimension(c.widthMm(), "widthMm").add(dimension(c.heightMm(), "heightMm"))
                .multiply(TWO).divide(THOUSAND);
    }

    private static BigDecimal dimension(BigDecimal value, String name) {
        return positive(value, name).setScale(0, RoundingMode.CEILING);
    }

    private static BigDecimal parameter(Map<String, BigDecimal> params, String name) {
        return nonNegative(params.get(name), name);
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
