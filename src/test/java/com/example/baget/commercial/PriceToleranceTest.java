package com.example.baget.commercial;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PriceToleranceTest {
 boolean check(String base,String actual){return PriceTolerance.requiresApproval(new BigDecimal(base),new BigDecimal(actual));}
 @Test void inclusiveBothBoundaries(){assertFalse(check("1000","950"));assertFalse(check("1000","1050"));}
 @Test void oneCentOutsideNeedsApproval(){assertTrue(check("1000","949.99"));assertTrue(check("1000","1050.01"));}
 @Test void unchangedAndSmallDifferencePass(){assertFalse(check("1000","1000"));assertFalse(check("1000","999.99"));}
 @Test void zeroBaselineIsExplicit(){assertFalse(check("0","0"));assertTrue(check("0","0.01"));}
 @Test void invalidAmountsRejected(){assertThrows(IllegalArgumentException.class,()->check("-1","0"));assertThrows(IllegalArgumentException.class,()->check("1","-1"));}
}
