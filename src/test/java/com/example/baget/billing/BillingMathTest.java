package com.example.baget.billing;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BillingMathTest {
 @Test void partialDebtIsAllocatedExactlyWithoutReopeningPaidMoney(){var r=BillingMath.remaining(List.of(new BigDecimal("100.00"),new BigDecimal("200.00")),new BigDecimal("120.00"));assertEquals(List.of(new BigDecimal("40.00"),new BigDecimal("80.00")),r);}
 @Test void zeroRowsAndCentRemaindersPreserveTotal(){var r=BillingMath.remaining(List.of(new BigDecimal("0.00"),new BigDecimal("0.01"),new BigDecimal("0.01")),new BigDecimal("0.01"));assertEquals(new BigDecimal("0.00"),r.get(0));assertEquals(new BigDecimal("0.01"),r.stream().reduce(BigDecimal.ZERO,BigDecimal::add));}
 @Test void repeatedMergesDoNotRestoreOriginalDebt(){var first=BillingMath.remaining(List.of(new BigDecimal("100.00"),new BigDecimal("200.00")),new BigDecimal("120.00"));var second=BillingMath.remaining(first,new BigDecimal("60.00"));assertEquals(List.of(new BigDecimal("20.00"),new BigDecimal("40.00")),second);}
 @Test void unexplainedDebtIncreaseRejected(){assertThrows(IllegalArgumentException.class,()->BillingMath.remaining(List.of(new BigDecimal("10.00")),new BigDecimal("11.00")));}
 @Test void randomizedConservation(){var random=new Random(57);for(int i=0;i<1000;i++){var weights=new ArrayList<BigDecimal>();long cents=0;for(int j=0;j<8;j++){int x=random.nextInt(1000);cents+=x;weights.add(BigDecimal.valueOf(x,2));}var debt=BigDecimal.valueOf(random.nextLong(cents+1),2);var result=BillingMath.remaining(weights,debt);assertEquals(0,debt.compareTo(result.stream().reduce(BigDecimal.ZERO,BigDecimal::add)));for(int j=0;j<weights.size();j++){assertTrue(result.get(j).signum()>=0);assertTrue(result.get(j).compareTo(weights.get(j))<=0);}}}
}
