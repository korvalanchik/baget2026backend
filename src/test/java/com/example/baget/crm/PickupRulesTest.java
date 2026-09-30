package com.example.baget.crm;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PickupRulesTest {
 final LocalDate today=LocalDate.of(2026,9,29);
 String status(Set<String> ready,Set<String> notified,String last,LocalDate date) {
  return PickupRules.status(ready,notified,ready,last,date,today);
 }
 @Test void newlyReadyProductNeedsAnotherNotice(){assertEquals("NEEDS_NOTICE",status(Set.of("A1","B1"),Set.of("A1"),"PICKUP_NOTIFIED",null));}
 @Test void reworkCycleNeedsNoticeAgain(){assertEquals("NEEDS_NOTICE",status(Set.of("A2"),Set.of("A1"),"PICKUP_NOTIFIED",null));}
 @Test void partialDeliveryDoesNotResetRemainingNotice(){assertEquals("NOTIFIED",status(Set.of("B1"),Set.of("A1","B1"),"PICKUP_NOTIFIED",null));}
 @Test void emptyReadySetNeverShowsOverdue(){assertEquals("NOT_READY",status(Set.of(),Set.of("A1"),"PICKUP_AGREED",today.minusDays(1)));}
 @Test void pickupTodayIsNotOverdue(){assertEquals("AGREED",status(Set.of("A1"),Set.of("A1"),"PICKUP_AGREED",today));assertEquals("OVERDUE",status(Set.of("A1"),Set.of("A1"),"PICKUP_AGREED",today.minusDays(1)));}
 @Test void unansweredCallIsVisibleAndDoesNotAcknowledgeReadiness(){assertEquals("NO_ANSWER",status(Set.of("A1"),Set.of(),"PICKUP_NO_ANSWER",null));assertTrue(PickupRules.needsNotice(Set.of("A1"),Set.of()));}
 @Test void lastUnansweredCallStillVisibleAfterAgreement(){assertEquals("NO_ANSWER",status(Set.of("A1"),Set.of("A1"),"PICKUP_NO_ANSWER",today));}
}
