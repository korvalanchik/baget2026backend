package com.example.baget.reconciliation;
import com.example.baget.ledger.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReconciliationMathTest {
 static BigDecimal n(String s){return new BigDecimal(s);}
 static ReconciliationEntry entry(String direction,String category,String amount){return new ReconciliationEntry(1L,OffsetDateTime.parse("2026-10-03T10:00:00Z"),1L,"Облік",LedgerDirection.valueOf(direction),LedgerCategory.valueOf(category),n(amount),null,null,null);}
 @Test void mergeAndAllocationDoNotCountAsNewCashOrNewInvoices(){
  var result=ReconciliationMath.totals(n("100"),List.of(entry("OUT","INVOICE_ISSUED","500"),entry("IN","PAYMENT_RECEIVED","200"),entry("IN","CUSTOMER_ADVANCE","100"),entry("OUT","INTERNAL_TRANSFER_OUT","100"),entry("IN","INTERNAL_TRANSFER_IN","100"),entry("IN","INVOICE_MERGE_IN","300"),entry("OUT","INVOICE_MERGE_OUT","300")));
  assertEquals(0,n("300").compareTo(result.get("closing")));assertEquals(0,n("300").compareTo(result.get("received")));assertEquals(0,n("500").compareTo(result.get("invoiced")));assertEquals(0,result.get("otherChange").signum());
  assertEquals(0,result.get("opening").add(result.get("debit")).subtract(result.get("credit")).compareTo(result.get("closing")));
 }
 @Test void emptyPeriodKeepsOpeningCredit(){var result=ReconciliationMath.totals(n("-40"),List.of());assertEquals(n("-40"),result.get("closing"));assertEquals(n("40"),result.get("closingCredit"));assertEquals(0,result.get("closingDebt").signum());}
 @Test void refundsAndAdjustmentsRemainVisible(){var result=ReconciliationMath.totals(n("0"),List.of(entry("OUT","CUSTOMER_REFUND","20"),entry("IN","ADJUSTMENT","5")));assertEquals(n("20"),result.get("refunded"));assertEquals(n("-5"),result.get("otherChange"));assertEquals(n("15"),result.get("closing"));}
 @Test void periodIsInclusiveAndLimited(){assertDoesNotThrow(()->ReconciliationMath.period(LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)));assertThrows(IllegalArgumentException.class,()->ReconciliationMath.period(LocalDate.of(2026,1,1),LocalDate.of(2027,1,2)));assertThrows(IllegalArgumentException.class,()->ReconciliationMath.period(LocalDate.of(2026,1,2),LocalDate.of(2026,1,1)));}
 @Test void kyivBoundariesRespectDaylightSaving(){assertEquals(23,Duration.between(ReconciliationMath.start(LocalDate.of(2026,3,29)),ReconciliationMath.start(LocalDate.of(2026,3,30))).toHours());assertEquals(25,Duration.between(ReconciliationMath.start(LocalDate.of(2026,10,25)),ReconciliationMath.start(LocalDate.of(2026,10,26))).toHours());}
 @Test void invalidAmountsAndDirectionAreRejected(){assertThrows(IllegalArgumentException.class,()->ReconciliationMath.delta("OTHER",n("1")));assertThrows(IllegalArgumentException.class,()->ReconciliationMath.delta("IN",n("-1")));}
}
