package com.example.baget.reconciliation;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
public final class ReconciliationMath {
 private ReconciliationMath() {}
 public static final ZoneId ZONE=ZoneId.of("Europe/Kyiv");
 public static void period(LocalDate from,LocalDate to){
  if(from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>365)
   throw new IllegalArgumentException("Оберіть період від 1 до 366 днів");
 }
 public static OffsetDateTime start(LocalDate date){return date.atStartOfDay(ZONE).toOffsetDateTime();}
 public static BigDecimal delta(String direction,BigDecimal amount){
  if(amount==null || amount.signum()<0 || !Set.of("IN","OUT").contains(direction))throw new IllegalArgumentException("Некоректна фінансова проводка");
  return "OUT".equals(direction)?amount:amount.negate();
 }
 public static String label(String category){return switch(category){
  case "INVOICE_ISSUED" -> "Виставлення рахунку";
  case "PAYMENT_RECEIVED" -> "Оплата від фірми";
  case "CUSTOMER_ADVANCE" -> "Аванс фірми";
  case "CUSTOMER_REFUND" -> "Повернення коштів";
  case "INVOICE_MERGE_IN" -> "Перенесення боргу зі старого рахунку";
  case "INVOICE_MERGE_OUT" -> "Перенесення боргу в новий рахунок";
  case "INTERNAL_TRANSFER_IN" -> "Внутрішнє зарахування";
  case "INTERNAL_TRANSFER_OUT" -> "Внутрішнє списання";
  case "INVOICE_REVENUE" -> "Дохід за рахунком";
  case "PAYMENT_ALLOCATION" -> "Розподіл оплати";
  case "APPLY_ADVANCE_TO_INVOICE" -> "Зарахування авансу";
  case "MATERIAL_PURCHASE" -> "Придбання матеріалів";
  case "OVERHEAD_EXPENSE" -> "Накладні витрати";
  case "ADJUSTMENT" -> "Коригування";
  default -> category;
 };}
 public static Map<String,BigDecimal> totals(BigDecimal opening,List<ReconciliationEntry> entries){
  var result=new LinkedHashMap<String,BigDecimal>();
  for(String k:List.of("opening","closing","invoiced","received","refunded","debit","credit","otherChange"))result.put(k,BigDecimal.ZERO);
  result.put("opening",opening);BigDecimal closing=opening;
  for(var e:entries){
   String direction=e.direction().name(),category=e.category().name();BigDecimal change=delta(direction,e.amount());closing=closing.add(change);
   String side="OUT".equals(direction)?"debit":"credit";result.put(side,result.get(side).add(e.amount()));
   String key="INVOICE_ISSUED".equals(category)&&"OUT".equals(direction)?"invoiced":Set.of("PAYMENT_RECEIVED","CUSTOMER_ADVANCE").contains(category)&&"IN".equals(direction)?"received":"CUSTOMER_REFUND".equals(category)&&"OUT".equals(direction)?"refunded":null;
   if(key!=null)result.put(key,result.get(key).add(e.amount()));else result.put("otherChange",result.get("otherChange").add(change));
  }
  result.put("closing",closing);result.put("closingDebt",closing.max(BigDecimal.ZERO));result.put("closingCredit",closing.negate().max(BigDecimal.ZERO));return result;
 }
}
