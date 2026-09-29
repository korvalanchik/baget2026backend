package com.example.baget.assortment;
import com.example.baget.procurement.ProcurementRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AssortmentDemandTest {
 BigDecimal n(int x){return BigDecimal.valueOf(x);}
 BigDecimal recommendation(int stock,int demand,int incoming,boolean frame,long branches){return ProcurementRules.assortmentNeed(n(stock),n(demand),n(Math.min(stock,demand)),n(incoming),n(5),n(10),frame,branches).recommended();}
 @Test void inactiveFrameHasNoSafetyStockPurchase(){assertEquals(n(0),recommendation(1,0,0,true,0));}
 @Test void inactiveFrameStillCoversCustomerOrders(){assertEquals(n(3),recommendation(1,4,0,true,0));}
 @Test void confirmedSupplyIsSubtracted(){assertEquals(n(1),recommendation(1,4,2,true,0));}
 @Test void multipleBranchesDoNotMultiplyPurchase(){assertEquals(n(9),recommendation(1,0,0,true,1));assertEquals(recommendation(1,0,0,true,1),recommendation(1,0,0,true,12));}
 @Test void otherMaterialsKeepExistingThresholds(){assertEquals(n(9),recommendation(1,0,0,false,0));}
 @Test void unknownStockRemainsUnknown(){assertNull(ProcurementRules.assortmentNeed(null,n(4),n(0),n(0),n(5),n(10),true,0).recommended());}
}
