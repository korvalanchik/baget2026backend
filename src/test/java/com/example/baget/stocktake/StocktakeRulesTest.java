package com.example.baget.stocktake;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StocktakeRulesTest {
 BigDecimal n(int x){return BigDecimal.valueOf(x);}
 StocktakeRules.Reservation row(long id,int demand,int reserved){return new StocktakeRules.Reservation(id,id,100+id,n(demand),n(reserved));}
 @Test void shortagePreservesOldestExistingReserve(){var a=StocktakeRules.allocate(n(4),List.of(row(2,5,3),row(1,5,3)));assertEquals(n(3),a.get(0).after());assertEquals(n(1),a.get(1).after());assertEquals(n(4),a.get(1).shortageAfter());}
 @Test void unfilledOldDemandCannotStealExistingYoungerReserve(){var a=StocktakeRules.allocate(n(3),List.of(row(1,5,0),row(2,5,3)));assertEquals(n(0),a.get(0).after());assertEquals(n(3),a.get(1).after());}
 @Test void surplusAllocatesFreeStockInOriginalQueue(){var a=StocktakeRules.allocate(n(8),List.of(row(1,5,1),row(2,6,2)));assertEquals(n(5),a.get(0).after());assertEquals(n(3),a.get(1).after());}
 @Test void zeroReleasesReserveButPreservesDemand(){var a=StocktakeRules.allocate(n(0),List.of(row(1,5,3)));assertEquals(n(0),a.get(0).after());assertEquals(n(5),a.get(0).demand());assertEquals(n(5),a.get(0).shortageAfter());}
 @Test void unknownBaselineIsNotAnInventedSurplus(){assertNull(StocktakeRules.difference(null,n(5)));assertEquals(n(-3),StocktakeRules.difference(n(8),n(5)));}
 @Test void invalidInputRejected(){assertThrows(IllegalArgumentException.class,()->StocktakeRules.allocate(n(-1),List.of()));assertThrows(IllegalArgumentException.class,()->StocktakeRules.allocate(n(2),List.of(row(1,1,2))));}
}
