package com.example.baget.calculation;

import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumptionCalculatorTest {
    private final ConsumptionCalculator calc = new ConsumptionCalculator();
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static CalculationContext context(String w, String h, int qty) {
        return new CalculationContext(n(w), n(h), qty, BigDecimal.ONE);
    }
    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @Test
    void mirrorAreaAndProductMultiplier() {
        var r = calc.calculate(CalculationMethod.AREA, UnitType.SQUARE_METER,
                null, Map.of(), context("600", "800", 3));
        equal("0.48", r.consumptionPerProduct());
        equal("1.44", r.totalConsumption());
    }
    @Test void frameUsesMetersAndRoundsProfileLikeLegacyApi() {
        var r = calc.calculate(CalculationMethod.FRAME_PROFILE, UnitType.METER,
                n("0.024"), Map.of(), context("600", "800", 2));
        equal("2.992", r.consumptionPerProduct());
        equal("5.984", r.totalConsumption());
        equal("2.992", calc.calculate(CalculationMethod.FRAME_PROFILE, UnitType.METER,
                n("0.0244"), Map.of(), context("600", "800", 1)).totalConsumption());
    }
    @Test void perimeterCeilsDimensionsLikeLegacyJs() {
        equal("2.804", calc.calculate(CalculationMethod.PERIMETER, UnitType.METER,
                null, Map.of(), context("600.1", "800.1", 1)).totalConsumption());
    }
    @Test void areaCeilsDimensionsLikeLegacyJs() {
        equal("0.481401", calc.calculate(CalculationMethod.AREA, UnitType.SQUARE_METER,
                null, Map.of(), context("600.1", "800.1", 1)).totalConsumption());
    }
    @Test void cordUsesRawWidthAndDoesNotRequireHeight() {
        var c = new CalculationContext(n("600.5"), null, 2, null);
        equal("1.401", calc.calculate(CalculationMethod.WIDTH_PLUS_ALLOWANCE, UnitType.METER,
                null, Map.of("allowanceMm", n("100")), c).totalConsumption());
    }
    @Test void canvasMarginIsTotalNotPerSide() {
        equal("0.5984", calc.calculate(CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER,
                null, Map.of("marginWidthMm", n("80"), "marginHeightMm", n("80")),
                context("600", "800", 1)).totalConsumption());
        equal("0.63", calc.calculate(CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER,
                null, Map.of("marginWidthMm", n("100"), "marginHeightMm", n("100")),
                context("600", "800", 1)).totalConsumption());
    }
    @Test void embroideryMargin() {
        equal("0.4941", calc.calculate(CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER,
                null, Map.of("marginWidthMm", n("10"), "marginHeightMm", n("10")),
                context("600", "800", 1)).totalConsumption());
    }
    @Test void twoHangersOnThreeProducts() {
        equal("6", calc.calculate(CalculationMethod.QUANTITY, UnitType.PIECE, null, Map.of(),
                new CalculationContext(null, null, 3, n("2"))).totalConsumption());
    }
    @Test void fixedWorkIsPerProduct() {
        equal("3", calc.calculate(CalculationMethod.FIXED, UnitType.PIECE, null, Map.of(),
                new CalculationContext(null, null, 3, null)).totalConsumption());
    }
    @Test void rejectsUnconfiguredPartAndWrongUnit() {
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(null, UnitType.METER,
                null, Map.of(), context("600", "800", 1)));
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(CalculationMethod.AREA,
                UnitType.METER, null, Map.of(), context("600", "800", 1)));
    }
    @Test void rejectsMissingNegativeAndUnexpectedParameters() {
        for (var p : java.util.List.of(Map.<String, BigDecimal>of(),
                Map.of("allowanceMm", n("-1")), Map.of("allowanceMM", n("100")))) {
            assertThrows(IllegalArgumentException.class, () -> calc.calculate(
                    CalculationMethod.WIDTH_PLUS_ALLOWANCE, UnitType.METER,
                    null, p, context("600", "800", 1)));
        }
    }
    @Test void rejectsInvalidDimensionsAndCounts() {
        assertThrows(IllegalArgumentException.class, () -> context("600", "800", 0));
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(CalculationMethod.AREA,
                UnitType.SQUARE_METER, null, Map.of(), context("0", "800", 1)));
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(CalculationMethod.QUANTITY,
                UnitType.PIECE, null, Map.of(), new CalculationContext(null, null, 1, n("1.5"))));
    }
}

