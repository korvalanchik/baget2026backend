package com.example.baget.orderproduct;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReservationMathTest {
    private static BigDecimal n(String value){return new BigDecimal(value);}
    @Test void shortageDoesNotRejectOrder(){assertEquals(n("4"),ReservationMath.allocate(n("20"),n("16"),n("6")));}
    @Test void unknownAndNegativeStockDoNotInventAvailableMaterial(){
        assertEquals(0,ReservationMath.allocate(null,n("0"),n("6")).signum());
        assertEquals(0,ReservationMath.allocate(n("-3"),n("0"),n("6")).signum());
    }
    @Test void replenishmentFillsOnlyMissingDemand(){assertEquals(n("2"),ReservationMath.allocate(n("22"),n("20"),n("2")));}
    @Test void roundingNeverCreatesNegativeRows(){
        var rows=AmountAllocation.reconcile(Collections.nCopies(50,n(".01")),n(".30"));
        assertTrue(rows.stream().allMatch(v->v.signum()>=0));
        assertEquals(n(".30"),rows.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void roundingCanAddOneCentToZeroDisplayedAmounts(){
        var rows=AmountAllocation.reconcile(List.of(n("0.00"),n("0.00")),n("0.01"));
        assertEquals(n("0.01"),rows.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
}
