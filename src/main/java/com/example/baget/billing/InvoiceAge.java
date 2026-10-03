package com.example.baget.billing;
import java.time.*;
import java.time.temporal.ChronoUnit;
/** Calendar days in Kyiv; this is invoice age, not overdue status. */
public final class InvoiceAge {
 private InvoiceAge() {}
 public static final ZoneId ZONE=ZoneId.of("Europe/Kyiv");
 public static long days(OffsetDateTime issued,LocalDate today){return Math.max(0,ChronoUnit.DAYS.between(issued.atZoneSameInstant(ZONE).toLocalDate(),today));}
}
