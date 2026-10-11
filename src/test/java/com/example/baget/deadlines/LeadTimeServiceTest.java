package com.example.baget.deadlines;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import com.example.baget.calculation.preview.PreviewException;
import static org.junit.jupiter.api.Assertions.*;
class LeadTimeServiceTest {
 @Test void skipsWeekendAndStartDay(){assertEquals(LocalDate.of(2026,10,12),LeadTimeService.addWorkingDays(LocalDate.of(2026,10,9),1));assertEquals(LocalDate.of(2026,10,16),LeadTimeService.addWorkingDays(LocalDate.of(2026,10,9),5));}
 @Test void startsOnWeekend(){assertEquals(LocalDate.of(2026,10,12),LeadTimeService.addWorkingDays(LocalDate.of(2026,10,11),1));}
 @Test void crossesYear(){assertEquals(LocalDate.of(2027,1,4),LeadTimeService.addWorkingDays(LocalDate.of(2026,12,31),2));}
 @Test void defaultsAndEqualLimitsAreValid(){assertDoesNotThrow(()->LeadTimeService.validate(new LeadTimeService.Settings(5,7,10,3,0)));assertDoesNotThrow(()->LeadTimeService.validate(new LeadTimeService.Settings(1,1,1,1,0)));}
 @Test void invalidOrderAndBoundsAreRejected(){assertThrows(PreviewException.class,()->LeadTimeService.validate(new LeadTimeService.Settings(8,7,10,3,0)));assertThrows(PreviewException.class,()->LeadTimeService.validate(new LeadTimeService.Settings(5,7,6,3,0)));assertThrows(PreviewException.class,()->LeadTimeService.validate(new LeadTimeService.Settings(0,7,10,3,0)));assertThrows(PreviewException.class,()->LeadTimeService.validate(new LeadTimeService.Settings(5,7,366,31,0)));}
}
