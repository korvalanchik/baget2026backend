package com.example.baget.cash;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class CashDayEndTest {
 Map<String,Object> last(long revision,String expected,String difference){return Map.of("lastLedgerId",revision,"expected",new BigDecimal(expected),"difference",new BigDecimal(difference));}
 @Test void noConfirmationForDay(){assertEquals("NONE",CashDayMath.confirmation(0,BigDecimal.ZERO,null));}
 @Test void unchangedCashIsConfirmed(){assertEquals("CONFIRMED",CashDayMath.confirmation(8,new BigDecimal("100.00"),last(8,"100","0")));}
 @Test void recordedShortageDoesNotBecomeCleanConfirmation(){assertEquals("DIFFERENCE",CashDayMath.confirmation(8,new BigDecimal("100"),last(8,"100","-10")));}
 @Test void zeroNetNewEntriesStillRequireRecount(){assertEquals("STALE",CashDayMath.confirmation(10,new BigDecimal("100"),last(8,"100","0")));}
 @Test void changedAmountAlsoInvalidatesConfirmation(){assertEquals("STALE",CashDayMath.confirmation(8,new BigDecimal("105"),last(8,"100","0")));}
 @Test void freshRecountRestoresConfirmation(){assertEquals("CONFIRMED",CashDayMath.confirmation(10,new BigDecimal("100"),last(10,"100","0")));}
}
