package com.example.baget.calculation;

import com.example.baget.parts.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrossbarCalculatorTest {
    private final CrossbarCalculator calculator = new CrossbarCalculator();
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual));
    }
    @Test void width46AndTwoProducts() {
        var r = calculator.calculate(n("1200"), n("1500"), n("46"), 2);
        assertEquals(2, r.segmentsPerProduct().size());
        eq("1408", r.segmentsPerProduct().get(0).lengthMm());
        eq("1108", r.segmentsPerProduct().get(1).lengthMm());
        eq("2.516", r.consumptionPerProduct());
        eq("5.032", r.totalConsumption());
    }
    @Test void width56() {
        eq("2.476", calculator.calculate(n("1200"), n("1500"), n("56"), 1).totalConsumption());
    }
    @Test void width28() {
        eq("2.588", calculator.calculate(n("1200"), n("1500"), n("28"), 1).totalConsumption());
    }
    @Test void belowThresholdHasNoCrossbars() {
        var r = calculator.calculate(n("999.9"), n("800"), n("46"), 1);
        assertTrue(r.segmentsPerProduct().isEmpty());
        eq("0", r.totalConsumption());
    }
    @Test void exactWidthThresholdCreatesHeightSegment() {
        var r = calculator.calculate(n("1000"), n("800"), n("46"), 1);
        assertEquals(1, r.segmentsPerProduct().size());
        assertEquals(CrossbarCalculator.Direction.ALONG_HEIGHT, r.segmentsPerProduct().get(0).direction());
        eq("0.708", r.totalConsumption());
    }
    @Test void exactHeightThresholdCreatesWidthSegment() {
        var r = calculator.calculate(n("800.5"), n("1000"), n("46"), 1);
        assertEquals(CrossbarCalculator.Direction.ALONG_WIDTH, r.segmentsPerProduct().get(0).direction());
        eq("0.7085", r.totalConsumption());
    }
    @Test void rejectsImpossibleGeometryAndMissingRailWidth() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(n("1000"), n("92"), n("46"), 1));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(n("1000"), n("800"), null, 1));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(n("1000"), n("800"), n("46"), 0));
    }
    @Test void perimeterAcceptsRailWidthWithoutChangingConsumption() {
        var c = new ConsumptionCalculator();
        var context = new CalculationContext(n("1200"), n("1500"), 2, BigDecimal.ONE);
        eq("10.8", c.calculate(CalculationMethod.PERIMETER, UnitType.METER, null,
                Map.of("railWidthMm", n("56")), context).totalConsumption());
        eq("10.8", c.calculate(CalculationMethod.PERIMETER, UnitType.METER, null,
                Map.of(), context).totalConsumption());
        assertThrows(IllegalArgumentException.class, () -> c.calculate(CalculationMethod.PERIMETER,
                UnitType.METER, null, Map.of("railWidthMm", n("0")), context));
    }
    @Test void serviceReadsWidthFromSelectedUnderframeAndReturnsRail28030() {
        var p = mock(Parts.class);
        when(p.getPartKind()).thenReturn(PartKind.MATERIAL);
        when(p.getUnitType()).thenReturn(UnitType.METER);
        when(p.getCalculationMethod()).thenReturn(CalculationMethod.PERIMETER);
        when(p.getCalculationParams()).thenReturn(Map.of("railWidthMm", n("56")));
        when(p.getProfilWidth()).thenReturn(0.0001);
        var service = new UnderframeCrossbarService(28030);
        var r = service.calculate(p, new CalculationContext(n("1200"), n("1500"), 1, BigDecimal.ONE));
        assertEquals(28030, r.partNo());
        eq("2.476", r.consumption().totalConsumption());
        verify(p, never()).getProfilWidth();
    }
}
