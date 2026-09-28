package com.example.baget.orderproduct;
import com.example.baget.parts.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkshopGeometryTest {
    private BigDecimal n(String s){return new BigDecimal(s);}
    @Test void sheetSizeUsesSameRoundingAsConsumption(){var s=WorkshopGeometry.size(CalculationMethod.AREA,UnitType.SQUARE_METER,n("400.1"),n("500"),Map.of(),null);assertEquals(n("401"),s.widthMm());assertEquals(n("500"),s.heightMm());}
    @Test void stretchingAllowanceIsTotalNotTwicePerSide(){var s=WorkshopGeometry.size(CalculationMethod.AREA_WITH_MARGIN,UnitType.SQUARE_METER,n("300"),n("400"),Map.of("marginWidthMm",n("10"),"marginHeightMm",n("10")),null);assertEquals(n("310"),s.widthMm());assertEquals(n("410"),s.heightMm());}
    @Test void frameExplainsProfileAllowanceWithoutChangingOpeningDimensions(){var s=WorkshopGeometry.size(CalculationMethod.FRAME_PROFILE,UnitType.METER,n("400"),n("500"),Map.of(),n("0.034"));assertEquals(n("400"),s.widthMm());assertTrue(s.note().contains("468"));assertTrue(s.note().contains("568"));}
    @Test void canvasUsesUsageSpecificAllowance(){assertEquals(n("400"),WorkshopGeometry.canvas(n("300"),n("400"),true,true).widthMm());assertEquals(n("380"),WorkshopGeometry.canvas(n("300"),n("400"),true,false).widthMm());assertEquals(n("300"),WorkshopGeometry.canvas(n("300"),n("400"),false,true).widthMm());}
    @Test void pieceAndCordDoNotInventAHeight(){var piece=WorkshopGeometry.size(CalculationMethod.QUANTITY,UnitType.PIECE,n("300"),n("400"),Map.of(),null);assertNull(piece.widthMm());assertNull(piece.heightMm());var cord=WorkshopGeometry.size(CalculationMethod.WIDTH_PLUS_ALLOWANCE,UnitType.METER,n("300.1"),n("400"),Map.of("allowanceMm",n("100")),null);assertNull(cord.heightMm());assertTrue(cord.note().contains("400.1"));}
}
