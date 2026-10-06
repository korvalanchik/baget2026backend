package com.example.baget.receiptcost;
import org.junit.jupiter.api.Test;
import java.math.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptCostRulesTest {
 private static BigDecimal n(String s){return new BigDecimal(s);}
 @Test void weightedAverageUsesPhysicalStockAndFullLandedValue(){assertEquals(n("103.333333"),ReceiptCostRules.average(n("10"),n("90"),n("20"),n("2200")));}
 @Test void unknownOldCostCannotBeTreatedAsZero(){assertThrows(IllegalArgumentException.class,()->ReceiptCostRules.average(n("10"),null,n("20"),n("2200")));assertEquals(n("110.000000"),ReceiptCostRules.average(BigDecimal.ZERO,null,n("20"),n("2200")));}
 @Test void centsAreDistributedWithoutLoss(){assertEquals(List.of(n("0.01"),n("0.01"),n("0.00")),ReceiptCostRules.allocate(List.of(n("1"),n("1"),n("1")),n("0.02")));}
 @Test void sharedCostNeedsNonzeroBase(){assertThrows(IllegalArgumentException.class,()->ReceiptCostRules.allocate(List.of(BigDecimal.ZERO),n("1")));}
}
