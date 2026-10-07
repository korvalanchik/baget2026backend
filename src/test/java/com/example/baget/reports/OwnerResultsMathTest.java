package com.example.baget.reports;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnerResultsMathTest {
 static Map<String,Object> row(String sale,String material,String reward,int unknown,String state){var r=new HashMap<String,Object>();r.put("saleAmount",sale);r.put("materialAmount",material);r.put("rewardAmount",reward);r.put("unknownMaterials",unknown);r.put("rewardState",state);return r;}
 @Test void deductsMaterialsAndAccruedRewards(){var s=OwnerResultsMath.summarize(List.of(row("1000","300","50",0,"POSTED")));assertEquals(new BigDecimal("650.00"),s.get("difference"));}
 @Test void missingCostsCannotLookLikeProfit(){var s=OwnerResultsMath.summarize(List.of(row("1000","0","50",1,"POSTED")));assertNull(s.get("difference"));assertEquals(1,s.get("incompleteCount"));}
 @Test void missingSaleAndPendingRewardPreventCompleteResult(){var s=OwnerResultsMath.summarize(List.of(row(null,"300","0",0,"REVIEW")));assertNull(s.get("difference"));assertEquals(1,s.get("unknownSales"));assertEquals(1,s.get("pendingRewards"));}
 @Test void zeroIsKnownAndNoAccrualIsNotInvented(){var s=OwnerResultsMath.summarize(List.of(row("0","0","0",0,null)));assertEquals(new BigDecimal("0.00"),s.get("difference"));}
 @Test void partialSummaryKeepsOnlyCompleteDifferences(){var s=OwnerResultsMath.summarize(List.of(row("1000","300","50",0,"POSTED"),row("800","100","40",1,"POSTED")));assertNull(s.get("difference"));assertEquals(new BigDecimal("650.00"),s.get("completeDifference"));assertEquals(new BigDecimal("1800.00"),s.get("knownSales"));}
}
