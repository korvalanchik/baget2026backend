package com.example.baget.crm;
import java.time.LocalDate;
import java.util.Set;
/** A successful contact covers the ready production cycles explicitly included in its snapshot. */
public final class PickupRules {
 private PickupRules(){}
 public static String status(Set<String> ready,Set<String> notified,Set<String> lastAttempt,
   String lastOutcome,LocalDate pickupDate,LocalDate today) {
  if(ready.isEmpty())return "NOT_READY";
  if(!notified.containsAll(ready))return "PICKUP_NO_ANSWER".equals(lastOutcome)&&lastAttempt.containsAll(ready)?"NO_ANSWER":"NEEDS_NOTICE";
  if("PICKUP_NO_ANSWER".equals(lastOutcome)&&lastAttempt.containsAll(ready))return "NO_ANSWER";
  if(pickupDate!=null)return pickupDate.isBefore(today)?"OVERDUE":"AGREED";
  return "NOTIFIED";
 }
 public static boolean needsNotice(Set<String> ready,Set<String> notified){return !ready.isEmpty()&&!notified.containsAll(ready);}
}
