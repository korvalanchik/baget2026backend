package com.example.baget.cash;

import java.math.BigDecimal;
import java.util.*;

/** Aggregates one database snapshot; no currency calculations use floating point. */
public final class CashDayMath {
 private CashDayMath() {}
 public record Movement(String category,String direction,BigDecimal amount) {}
 public record Snapshot(BigDecimal opening,BigDecimal incoming,BigDecimal outgoing,
                        BigDecimal expected,long lastLedgerId,List<Movement> movements) {}
 public static Snapshot summarize(List<Map<String,Object>> rows) {
  BigDecimal opening=BigDecimal.ZERO,in=BigDecimal.ZERO,out=BigDecimal.ZERO;
  long last=0;var movements=new ArrayList<Movement>();
  for(var row:rows) {
   BigDecimal amount=new BigDecimal(row.get("amount").toString());
   String direction=row.get("direction").toString(),period=row.get("period").toString();
   if(amount.signum()<0||!Set.of("IN","OUT").contains(direction)||!Set.of("BEFORE","DAY").contains(period))
    throw new IllegalArgumentException("Некоректна проводка каси");
   last=Math.max(last,((Number)row.get("lastId")).longValue());
   if("BEFORE".equals(period))opening=opening.add("IN".equals(direction)?amount:amount.negate());
   else {
    if("IN".equals(direction))in=in.add(amount);else out=out.add(amount);
    movements.add(new Movement(row.get("category").toString(),direction,amount));
   }
  }
  return new Snapshot(opening,in,out,opening.add(in).subtract(out),last,List.copyOf(movements));
 }
 /** A zero-net transfer/reversal still invalidates a confirmation through its ledger id. */
 public static String confirmation(long revision,BigDecimal expected,Map<String,Object> latest) {
  if(latest==null)return "NONE";
  if(((Number)latest.get("lastLedgerId")).longValue()!=revision||new BigDecimal(latest.get("expected").toString()).compareTo(expected)!=0)return "STALE";
  return new BigDecimal(latest.get("difference").toString()).signum()==0?"CONFIRMED":"DIFFERENCE";
 }
 public static void verify(Snapshot current,long expectedRevision,BigDecimal expectedAmount,BigDecimal actual,String note) {
  if(current.lastLedgerId()!=expectedRevision||expectedAmount==null||current.expected().compareTo(expectedAmount)!=0)
   throw new IllegalArgumentException("Рух коштів змінився. Оновіть звірку і перерахуйте готівку");
  if(actual==null||actual.signum()<0||actual.compareTo(new BigDecimal("999999999.99"))>0||actual.stripTrailingZeros().scale()>2)
   throw new IllegalArgumentException("Вкажіть фактичний залишок до двох знаків після коми");
  if(actual.compareTo(current.expected())!=0&&(note==null||note.isBlank()))
   throw new IllegalArgumentException("Для розбіжності додайте примітку");
 }
}
