package com.example.baget.cash;
import java.math.BigDecimal;
import java.util.*;
public final class ReceiptPaymentMath {
 private ReceiptPaymentMath(){}
 public record Due(String component,BigDecimal total,BigDecimal paid,BigDecimal remaining){}
 public static List<Due> dues(Map<String,BigDecimal> totals,List<Map<String,Object>> payments){
  var paid=new HashMap<String,BigDecimal>();
  for(var p:payments){String kind=p.get("kind").toString(),component=p.get("component").toString();
   if(!totals.containsKey(component)||!Set.of("PAYMENT","REVERSAL").contains(kind))throw new IllegalArgumentException("Некоректна історія оплат");
   if(p.get("amount")==null)throw new IllegalArgumentException("Відсутня фінансова проводка оплати");
   if(p.get("direction")!=null&&!Objects.equals(p.get("direction"),"PAYMENT".equals(kind)?"OUT":"IN"))throw new IllegalArgumentException("Некоректний напрям фінансової проводки");
   BigDecimal amount=new BigDecimal(p.get("amount").toString());if(amount.signum()<=0)throw new IllegalArgumentException("Некоректна сума оплати");
   paid.merge(component,"PAYMENT".equals(kind)?amount:amount.negate(),BigDecimal::add);
  }
  var rows=new ArrayList<Due>();for(var e:totals.entrySet()){
   BigDecimal total=e.getValue(),used=paid.getOrDefault(e.getKey(),BigDecimal.ZERO);
   if(total.signum()<0||used.signum()<0||used.compareTo(total)>0)throw new IllegalArgumentException("Оплати не відповідають сумам приходу");
   rows.add(new Due(e.getKey(),total,used,total.subtract(used)));
  }return List.copyOf(rows);
 }
 public static void validate(List<Due> dues,String component,BigDecimal amount,long expectedRevision,long revision){
  if(expectedRevision!=revision)throw new IllegalArgumentException("Оплати змінилися. Оновіть документ перед новою оплатою");
  if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>2||amount.compareTo(new BigDecimal("999999999.99"))>0)throw new IllegalArgumentException("Вкажіть додатну суму до двох знаків після коми");
  var due=dues.stream().filter(d->d.component().equals(component)).findFirst().orElseThrow(()->new IllegalArgumentException("Оберіть складову закупівлі"));
  if(amount.compareTo(due.remaining())>0)throw new IllegalArgumentException("Сума перевищує незареєстрований залишок цієї складової");
 }
}
