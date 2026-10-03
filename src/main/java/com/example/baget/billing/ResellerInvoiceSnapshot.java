package com.example.baget.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static com.example.baget.billing.BillingAccess.conflict;

/** Uses confirmed quantities and saved order prices, never today's Parts prices. */
public final class ResellerInvoiceSnapshot {
 private ResellerInvoiceSnapshot() {}
 public static ObjectNode build(JsonNode confirmed,List<Map<String,Object>> rates) {
  if(!confirmed.isObject() || !confirmed.path("lines").isArray() || confirmed.path("lines").isEmpty())
   throw conflict("У підтвердженому розрахунку немає позицій для реселлерського рахунку");
  var byItem=new HashMap<Long,Map<String,Object>>();
  for(var rate:rates)if(byItem.put(((Number)rate.get("itemNo")).longValue(),rate)!=null)throw conflict("Повтор позиції ціни");
  ObjectNode result=confirmed.deepCopy();var lines=result.putArray("resellerLines");
  var seen=new HashSet<Long>();BigDecimal total=BigDecimal.ZERO;
  for(var line:confirmed.path("lines")){
   long id=line.path("itemNo").asLong();var rate=byItem.get(id);
   if(!seen.add(id) || rate==null || line.path("partNo").asLong()!=((Number)rate.get("partNo")).longValue())throw conflict("Збережені ціни не відповідають підтвердженому складу виробу");
   if(!line.path("actual").isNumber() || line.path("actual").decimalValue().signum()<0)throw conflict("Не підтверджено витрати позиції "+id);
   boolean internal="REWORK_INTERNAL".equals(line.path("role").asText());
   if(!internal && rate.get("price")==null)throw conflict("Для позиції "+id+" немає збереженої реселлерської ціни. Потрібне уточнення розрахунку");
   BigDecimal price=internal?BigDecimal.ZERO:new BigDecimal(rate.get("price").toString());
   if(price.signum()<0)throw conflict("Від’ємна реселлерська ціна позиції "+id);
   BigDecimal quantity=line.path("actual").decimalValue();
   BigDecimal amount=quantity.multiply(price).setScale(2,RoundingMode.HALF_UP);
   total=total.add(amount);
   lines.addObject().put("itemNo",id).put("partNo",line.path("partNo").asLong()).put("quantity",quantity).put("unitPrice",price).put("amount",amount);
  }
  if(seen.size()!=byItem.size())throw conflict("Склад виробу змінився після підтвердження");
  result.put("billingMode","RESELLER");result.set("retailTotal",confirmed.path("total"));result.put("resellerTotal",total);
  return result;
 }
}
