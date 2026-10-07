package com.example.baget.reports;
import java.math.*;
import java.util.*;
public final class OwnerResultsMath {
 private OwnerResultsMath(){}
 private static BigDecimal amount(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
 private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
 public static Map<String,Object> summarize(List<Map<String,Object>> rows){
  BigDecimal sales=BigDecimal.ZERO,materials=BigDecimal.ZERO,rewards=BigDecimal.ZERO,difference=BigDecimal.ZERO;int incomplete=0,unknownSales=0,unknownMaterials=0,pendingRewards=0;
  for(var row:rows){
   boolean saleKnown=row.get("saleAmount")!=null,materialsKnown=amount(row.get("unknownMaterials")).signum()==0,rewardsKnown=!"REVIEW".equals(row.get("rewardState"));
   BigDecimal sale=amount(row.get("saleAmount")),material=amount(row.get("materialAmount")),reward=amount(row.get("rewardAmount"));
   sales=sales.add(sale);materials=materials.add(material);rewards=rewards.add(reward);
   if(!saleKnown)unknownSales++;if(!materialsKnown)unknownMaterials++;if(!rewardsKnown)pendingRewards++;
   boolean complete=saleKnown&&materialsKnown&&rewardsKnown;row.put("complete",complete);row.put("materialAmount",money(material));row.put("rewardAmount",money(reward));row.put("difference",complete?money(sale.subtract(material).subtract(reward)):null);
   if(complete)difference=difference.add(sale.subtract(material).subtract(reward));else incomplete++;
  }
  var out=new LinkedHashMap<String,Object>();out.put("productCount",rows.size());out.put("knownSales",money(sales));out.put("knownMaterials",money(materials));out.put("rewards",money(rewards));out.put("incompleteCount",incomplete);out.put("unknownSales",unknownSales);out.put("unknownMaterials",unknownMaterials);out.put("pendingRewards",pendingRewards);out.put("completeDifference",money(difference));out.put("difference",incomplete==0?money(difference):null);return out;
 }
}
