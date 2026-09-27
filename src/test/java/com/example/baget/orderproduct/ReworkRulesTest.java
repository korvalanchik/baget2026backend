package com.example.baget.orderproduct;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.baget.orderproduct.ReworkRules.ChargeMode.*;

class ReworkRulesTest {
    private BigDecimal n(String value) {return new BigDecimal(value);}
    @Test void workshopExpenseDoesNotIncreaseCustomerPrice() {
        assertEquals(n("0.00"),ReworkRules.charge(WORKSHOP,n("2.25"),n("15.50")));
        assertEquals(n("34.88"),ReworkRules.charge(CUSTOMER,n("2.25"),n("15.50")));
    }
    @Test void additionalQuantitiesArePositiveAndPiecesAreIntegral() {
        assertDoesNotThrow(()->ReworkRules.quantity("METER",n("0.000001")));
        assertDoesNotThrow(()->ReworkRules.quantity("PIECE",n("2.000000")));
        for(String q:new String[]{"0","-1","1000000000","0.0000001"})assertThrows(IllegalArgumentException.class,()->ReworkRules.quantity("METER",n(q)));
        assertThrows(IllegalArgumentException.class,()->ReworkRules.quantity("PIECE",n("1.5")));
    }
    @Test void onlyConfirmedUnbilledProductsPhysicallyInWorkshopCanReopen() {
        assertTrue(ReworkRules.canOpen(true,"COMPLETED","WORKSHOP",null,false,false));
        assertFalse(ReworkRules.canOpen(true,"COMPLETED","WAREHOUSE",null,false,false));
        assertFalse(ReworkRules.canOpen(true,"COMPLETED","WORKSHOP","WAREHOUSE",false,false));
        assertFalse(ReworkRules.canOpen(true,"COMPLETED","WORKSHOP",null,true,false));
        assertFalse(ReworkRules.canOpen(true,"COMPLETED","WORKSHOP",null,false,true));
        assertFalse(ReworkRules.canOpen(false,"COMPLETED","WORKSHOP",null,false,false));
        assertFalse(ReworkRules.canOpen(true,"IN_PROGRESS","WORKSHOP",null,false,false));
    }
}
