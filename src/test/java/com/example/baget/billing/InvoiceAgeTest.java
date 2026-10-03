package com.example.baget.billing;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class InvoiceAgeTest {
 @Test void countsCalendarDaysNotFull24HourPeriods(){assertEquals(1,InvoiceAge.days(OffsetDateTime.parse("2026-10-02T23:59:00+03:00"),LocalDate.of(2026,10,3)));}
 @Test void utcTimestampIsConvertedToKyivFirst(){assertEquals(0,InvoiceAge.days(OffsetDateTime.parse("2026-10-02T22:30:00Z"),LocalDate.of(2026,10,3)));}
 @Test void winterOffsetIsHandled(){assertEquals(0,InvoiceAge.days(OffsetDateTime.parse("2026-01-02T22:30:00Z"),LocalDate.of(2026,1,3)));}
 @Test void futureDateDoesNotProduceNegativeAge(){assertEquals(0,InvoiceAge.days(OffsetDateTime.parse("2026-10-04T10:00:00Z"),LocalDate.of(2026,10,3)));}
 @Test void daylightSavingDoesNotChangeCalendarCount(){assertEquals(1,InvoiceAge.days(OffsetDateTime.parse("2026-03-29T00:00:00+02:00"),LocalDate.of(2026,3,30)));}
}
