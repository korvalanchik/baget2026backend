package com.example.baget.warehouse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class WarehouseRulesTest {
    private BigDecimal n(String s){return new BigDecimal(s);}
    @Test void receiptAddsButCountReplaces(){assertEquals(n("12"),WarehouseRules.stockAfter(n("10"),"RECEIPT",n("2")));assertEquals(n("2"),WarehouseRules.stockAfter(n("10"),"COUNT",n("2")));}
    @Test void unknownStockNeedsCount(){assertThrows(IllegalArgumentException.class,()->WarehouseRules.stockAfter(null,"RECEIPT",n("2")));assertEquals(n("0"),WarehouseRules.stockAfter(null,"COUNT",n("0")));}
    @Test void invalidMovementsRejected(){assertThrows(IllegalArgumentException.class,()->WarehouseRules.stockAfter(n("10"),"RECEIPT",n("0")));assertThrows(IllegalArgumentException.class,()->WarehouseRules.stockAfter(n("10"),"COUNT",n("-1")));assertThrows(IllegalArgumentException.class,()->WarehouseRules.stockAfter(n("10"),"OTHER",n("1")));}
    @Test void quantitiesCompareWithoutScale(){assertTrue(WarehouseRules.same(n("10.000000"),n("10")));assertFalse(WarehouseRules.same(null,n("0")));}
    @Test void routeRequiresWarehouse(){assertTrue(WarehouseRules.route("WORKSHOP","WAREHOUSE"));assertTrue(WarehouseRules.route("WAREHOUSE","BRANCH"));assertTrue(WarehouseRules.route("BRANCH","CUSTOMER"));assertFalse(WarehouseRules.route("WORKSHOP","CUSTOMER"));assertTrue(WarehouseRules.route("WAREHOUSE","WORKSHOP"));assertTrue(WarehouseRules.route("BRANCH","WORKSHOP"));assertFalse(WarehouseRules.route("CUSTOMER","WORKSHOP"));}
    @Test void closedRequestsCannotReopen(){assertTrue(WarehouseRules.requestTransition("OPEN","ORDERED"));assertTrue(WarehouseRules.requestTransition("ORDERED","CLOSED"));assertFalse(WarehouseRules.requestTransition("CLOSED","ORDERED"));}
}
