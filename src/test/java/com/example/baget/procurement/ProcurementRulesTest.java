package com.example.baget.procurement;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProcurementRulesTest {
    private BigDecimal n(String s){return new BigDecimal(s);}
    private ProcurementRules.Need need(String stock,String demand,String reserve,String incoming,String min,String target){return ProcurementRules.need(stock==null?null:n(stock),n(demand),n(reserve),n(incoming),n(min),n(target));}
    @Test void reserveIsNotSubtractedTwice(){assertEquals(n("5"),need("10","12","10","0","2","3").recommended());}
    @Test void incomingPreventsDuplicatePurchase(){assertEquals(n("0"),need("10","12","10","8","2","3").recommended());}
    @Test void deficitCanRemainWhileSupplyIsAlreadyOrdered(){var r=need("0","12","0","15","2","3");assertEquals(n("12"),r.shortage());assertEquals(n("0"),r.recommended());}
    @Test void targetIsReachedOnlyWhenMinimumTriggered(){assertEquals(n("0"),need("7","3","3","0","2","10").recommended());assertEquals(n("9"),need("4","3","3","0","2","10").recommended());}
    @Test void exactMinimumDoesNotTrigger(){assertEquals(n("0"),need("5","3","3","0","2","10").recommended());}
    @Test void unknownStockHasNoAutomaticRecommendation(){var r=need(null,"3","0","0","2","10");assertNull(r.recommended());assertNull(r.free());assertEquals(n("3"),r.shortage());}
    @Test void pieceQuantitiesMustBeWhole(){assertThrows(IllegalArgumentException.class,()->ProcurementRules.quantity("PIECE",n("1.2"),false));assertDoesNotThrow(()->ProcurementRules.quantity("PIECE",n("2.000000"),false));}
    @Test void invalidUnitsAndPrecisionRejected(){assertThrows(IllegalArgumentException.class,()->ProcurementRules.quantity(null,n("2"),false));assertThrows(IllegalArgumentException.class,()->ProcurementRules.quantity("METER",n("0.0000001"),false));}
    @Test void receivedQuantityCannotExceedRemainder(){assertThrows(IllegalArgumentException.class,()->ProcurementRules.receive("PARTIAL",n("10"),n("7"),n("4")));assertDoesNotThrow(()->ProcurementRules.receive("PARTIAL",n("10"),n("7"),n("3")));}
    @Test void closedDraftAndCancelledCannotReceive(){for(String s:new String[]{"DRAFT","CANCELLED","CLOSED","RECEIVED"})assertThrows(IllegalArgumentException.class,()->ProcurementRules.receive(s,n("10"),n("0"),n("1")));}
}
