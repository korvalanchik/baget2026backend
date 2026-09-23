package com.example.baget.calculation.product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PerimeterFastenersTest {
    @Test void minimumAndCeilingAtBoundaries() {
        assertEquals(4,PerimeterFasteners.count(new BigDecimal("50"),new BigDecimal("50"),200));
        assertEquals(4,PerimeterFasteners.count(new BigDecimal("200"),new BigDecimal("200"),200));
        assertEquals(5,PerimeterFasteners.count(new BigDecimal("200.001"),new BigDecimal("200"),200));
        assertEquals(9,PerimeterFasteners.count(new BigDecimal("400"),new BigDecimal("500"),200));
        assertEquals(4,PerimeterFasteners.count(new BigDecimal("300"),new BigDecimal("400"),400));
        assertEquals(5,PerimeterFasteners.count(new BigDecimal("400.001"),new BigDecimal("400"),400));
    }
}
