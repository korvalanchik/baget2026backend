package com.example.baget.receiptcost;
import java.math.*;
import java.util.*;
public final class ReceiptCostRules {
 private ReceiptCostRules(){}
 public static BigDecimal money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP);}
 public static List<BigDecimal> allocate(List<BigDecimal> amounts,BigDecimal expenses){
  if(amounts.isEmpty() || expenses.signum()<0 || amounts.stream().anyMatch(n->n.signum()<0))throw new IllegalArgumentException("Некоректні суми накладної");
  expenses=expenses.setScale(2,RoundingMode.UNNECESSARY);
  BigDecimal sum=amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
  if(sum.signum()==0){if(expenses.signum()!=0)throw new IllegalArgumentException("Спільні витрати неможливо розподілити за нульовою закупівельною сумою");return Collections.nCopies(amounts.size(),new BigDecimal("0.00"));}
  var shares=new ArrayList<BigDecimal>();var residuals=new ArrayList<BigDecimal>();BigDecimal used=BigDecimal.ZERO;
  for(var value:amounts){var share=expenses.multiply(value).divide(sum,2,RoundingMode.DOWN);shares.add(share);used=used.add(share);residuals.add(expenses.multiply(value).subtract(share.multiply(sum)));}
  int cents=expenses.subtract(used).movePointRight(2).intValueExact();var indices=new ArrayList<Integer>();for(int i=0;i<amounts.size();i++)indices.add(i);
  indices.sort((a,b)->{int result=residuals.get(b).compareTo(residuals.get(a));return result==0?Integer.compare(a,b):result;});
  for(int i=0;i<cents;i++){int j=indices.get(i);shares.set(j,shares.get(j).add(new BigDecimal("0.01")));}return List.copyOf(shares);
 }
 public static BigDecimal average(BigDecimal stock,BigDecimal oldCost,BigDecimal incoming,BigDecimal landed){
  if(stock==null||stock.signum()<0||incoming.signum()<=0||landed.signum()<0 || stock.signum()>0&&(oldCost==null||oldCost.signum()<0))throw new IllegalArgumentException("Вкажіть фактичний залишок і собівартість наявного матеріалу");
  return stock.multiply(stock.signum()==0?BigDecimal.ZERO:oldCost).add(landed).divide(stock.add(incoming),6,RoundingMode.HALF_UP);
 }
}
