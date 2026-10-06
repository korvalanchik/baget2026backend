package com.example.baget.costupdate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class CostUpdateRulesTest {
 @Test void purchaseCostsAreNotAddedTwice(){assertEquals(new BigDecimal("120.00"),CostUpdateRules.calculate(new BigDecimal("100"),new BigDecimal("20")));}
 @Test void roundsOnlyFinalValue(){assertEquals(new BigDecimal("1.01"),CostUpdateRules.calculate(new BigDecimal("1.005"),BigDecimal.ZERO));}
 @Test void unknownAndNegativeAreRejected(){assertThrows(IllegalArgumentException.class,()->CostUpdateRules.calculate(null,BigDecimal.ZERO));assertThrows(IllegalArgumentException.class,()->CostUpdateRules.calculate(BigDecimal.ONE,new BigDecimal("-1")));}
 @Test void freeMaterialIsKnown(){assertEquals(new BigDecimal("0.00"),CostUpdateRules.calculate(BigDecimal.ZERO,new BigDecimal("20")));}
}
