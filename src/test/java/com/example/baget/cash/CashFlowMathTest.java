package com.example.baget.cash;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CashFlowMathTest {
 static Map<String,Object> row(String period,String bucket,String direction,String amount){return Map.of("period",period,"bucket",bucket,"direction",direction,"amount",new BigDecimal(amount));}
 static void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
 @Test void fullMoneyScenarioKeepsTransfersAndCorrectionsOutOfExternalTurnover(){
  var branch=CashFlowMath.summarize(List.of(row("BEFORE","INITIAL","IN","1000"),row("PERIOD","EXTERNAL","IN","300"),row("PERIOD","EXTERNAL","IN","200"),row("PERIOD","TRANSFER","OUT","400")));
  var central=CashFlowMath.summarize(List.of(row("BEFORE","INITIAL","IN","500"),row("PERIOD","TRANSFER","IN","400"),row("PERIOD","EXTERNAL","OUT","250"),row("PERIOD","EXTERNAL","OUT","100"),row("PERIOD","EXTERNAL","OUT","30"),row("PERIOD","REVERSAL","IN","100")));
  var transit=CashFlowMath.summarize(List.of(row("PERIOD","TRANSFER","IN","400"),row("PERIOD","TRANSFER","OUT","400")));
  var total=CashFlowMath.combine(List.of(branch,central,transit));money("1100",branch.closing());money("620",central.closing());money("0",transit.closing());money("1500",total.opening());money("500",total.incoming());money("380",total.outgoing());money("100",total.reversalIn());money("800",total.transferIn());money("800",total.transferOut());money("1720",total.closing());
 }
 @Test void sentButUnreceivedTransferRemainsEnterpriseMoney(){var branch=CashFlowMath.summarize(List.of(row("BEFORE","INITIAL","IN","1000"),row("PERIOD","TRANSFER","OUT","400")));var transit=CashFlowMath.summarize(List.of(row("PERIOD","TRANSFER","IN","400")));var total=CashFlowMath.combine(List.of(branch,transit));money("1000",total.closing());money("0",total.incoming());money("0",total.outgoing());money("400",transit.closing());}
 @Test void openingPostedWithinPeriodIsNotIncome(){var total=CashFlowMath.summarize(List.of(row("PERIOD","INITIAL","IN","1000"),row("PERIOD","EXTERNAL","IN","0.10"),row("PERIOD","EXTERNAL","IN","0.20")));money("0",total.opening());money("1000",total.initialAdded());money("0.30",total.incoming());money("1000.30",total.closing());}
 @Test void priorTransfersAndReversalsBelongToOpeningBalance(){var total=CashFlowMath.summarize(List.of(row("BEFORE","TRANSFER","IN","100"),row("BEFORE","EXTERNAL","OUT","30"),row("BEFORE","REVERSAL","IN","10")));money("80",total.opening());money("80",total.closing());money("0",total.reversalIn());}
 @Test void emptyReportIsZero(){money("0",CashFlowMath.combine(List.of()).closing());money("0",CashFlowMath.summarize(List.of()).closing());}
 @Test void corruptGroupsAreRejected(){assertThrows(IllegalArgumentException.class,()->CashFlowMath.summarize(List.of(row("PERIOD","EXTERNAL","IN","-1"))));assertThrows(IllegalArgumentException.class,()->CashFlowMath.summarize(List.of(row("PERIOD","EXTERNAL","INVALID","1"))));}
}
