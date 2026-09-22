package com.example.baget.orderproduct;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProductionRulesTest {
    private BigDecimal n(String s){return new BigDecimal(s);}
    @Test void actualConsumptionDeterminesAmount(){assertEquals(n("55.00"),ProductionRules.amount(n("5.5"),n("10")));}
    @Test void roundsOnlyMoney(){assertEquals(n("0.01"),ProductionRules.amount(n("0.005"),n("1")));}
    @Test void negativeConsumptionRejected(){assertThrows(IllegalArgumentException.class,()->ProductionRules.amount(n("-1"),n("5")));}
    @Test void refusalIsNotProduction(){assertFalse(ProductionRules.transition(5,2,false));}
    @Test void cannotReopenManagerEditing(){assertFalse(ProductionRules.transition(2,1,false));}
    @Test void actualCorrectionsBeforePosting(){assertTrue(ProductionRules.transition(3,2,false));}
    @Test void cannotCancelConsumedMaterialsImplicitly(){assertFalse(ProductionRules.transition(6,5,true));}
    @Test void deliveryDoesNotRequirePaymentStatus(){assertTrue(ProductionRules.transition(7,11,true));assertTrue(ProductionRules.transition(11,12,true));}
    @Test void otherOrdersReservationsRemainProtected(){assertFalse(ProductionRules.canConsume(n("10"),n("6"),n("5")));assertTrue(ProductionRules.canConsume(n("10"),n("6"),n("4")));}
    @Test void unknownStockCannotBeConsumed(){assertFalse(ProductionRules.canConsume(null,n("0"),n("1")));assertTrue(ProductionRules.canConsume(null,n("0"),n("0")));}
}
