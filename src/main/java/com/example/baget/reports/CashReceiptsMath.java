package com.example.baget.reports;
import java.math.*;
import java.util.*;
public final class CashReceiptsMath {
 private CashReceiptsMath(){}
 public static String kind(String category,String direction){if("IN".equals(direction)){if("PAYMENT_RECEIVED".equals(category))return "PAYMENT";if("CUSTOMER_ADVANCE".equals(category))return "ADVANCE";}if("OUT".equals(direction)&&"CUSTOMER_REFUND".equals(category))return "REFUND";return null;}
 public static Map<String,Object> summarize(List<Map<String,Object>> rows){BigDecimal payment=BigDecimal.ZERO,advance=BigDecimal.ZERO,refund=BigDecimal.ZERO;int count=0;for(var r:rows){String k=kind(Objects.toString(r.get("category"),""),Objects.toString(r.get("direction"),""));if(k==null)continue;BigDecimal amount=new BigDecimal(r.get("amount").toString());if(amount.signum()<0)throw new IllegalArgumentException("Від'ємна сума проводки потребує перевірки");count++;switch(k){case "PAYMENT"->payment=payment.add(amount);case "ADVANCE"->advance=advance.add(amount);case "REFUND"->refund=refund.add(amount);default->throw new IllegalStateException();}r.put("kind",k);r.put("signedAmount","REFUND".equals(k)?amount.negate():amount);}return Map.of("payment",payment,"advance",advance,"refund",refund,"received",payment.add(advance),"net",payment.add(advance).subtract(refund),"count",count);}
}
