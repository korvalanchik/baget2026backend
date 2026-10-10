package com.example.baget.cash;

import java.math.BigDecimal;
import java.util.*;

/** Cash accounts only; invoice debt and advance allocations have no cash_account_id. */
public final class CashFlowMath {
 private CashFlowMath() {}
 public record Totals(BigDecimal opening,BigDecimal initialAdded,BigDecimal incoming,BigDecimal outgoing,
                      BigDecimal transferIn,BigDecimal transferOut,BigDecimal reversalIn,BigDecimal reversalOut,
                      BigDecimal closing) {}
 public static Totals summarize(List<Map<String,Object>> groups){
  BigDecimal opening=BigDecimal.ZERO,initial=BigDecimal.ZERO,in=BigDecimal.ZERO,out=BigDecimal.ZERO,
   ti=BigDecimal.ZERO,to=BigDecimal.ZERO,ri=BigDecimal.ZERO,ro=BigDecimal.ZERO;
  for(var row:groups){
   if(row.get("amount")==null)throw new IllegalArgumentException("Відсутня сума касової проводки");
   BigDecimal amount=new BigDecimal(row.get("amount").toString());
   String period=Objects.toString(row.get("period"),""),direction=Objects.toString(row.get("direction"),""),bucket=Objects.toString(row.get("bucket"),"");
   if(amount.signum()<0||!Set.of("BEFORE","PERIOD").contains(period)||!Set.of("IN","OUT").contains(direction)||!Set.of("INITIAL","TRANSFER","REVERSAL","EXTERNAL").contains(bucket))throw new IllegalArgumentException("Некоректна касова проводка");
   boolean incoming="IN".equals(direction);
   if("BEFORE".equals(period)){opening=opening.add(incoming?amount:amount.negate());continue;}
   switch(bucket){
    case "INITIAL" -> initial=initial.add(incoming?amount:amount.negate());
    case "TRANSFER" -> {if(incoming)ti=ti.add(amount);else to=to.add(amount);}
    case "REVERSAL" -> {if(incoming)ri=ri.add(amount);else ro=ro.add(amount);}
    default -> {if(incoming)in=in.add(amount);else out=out.add(amount);}
   }
  }
  return new Totals(opening,initial,in,out,ti,to,ri,ro,opening.add(initial).add(in).subtract(out).add(ti).subtract(to).add(ri).subtract(ro));
 }
 public static Totals combine(List<Totals> rows){
  BigDecimal[] v=new BigDecimal[9];Arrays.fill(v,BigDecimal.ZERO);
  for(var t:rows){BigDecimal[] a={t.opening(),t.initialAdded(),t.incoming(),t.outgoing(),t.transferIn(),t.transferOut(),t.reversalIn(),t.reversalOut(),t.closing()};for(int i=0;i<v.length;i++)v[i]=v[i].add(a[i]);}
  return new Totals(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8]);
 }
}
