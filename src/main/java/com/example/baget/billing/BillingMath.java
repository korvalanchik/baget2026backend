package com.example.baget.billing;
import java.math.*;
import java.util.*;
public final class BillingMath {
 private BillingMath(){}
 public static List<BigDecimal> remaining(List<BigDecimal> amounts,BigDecimal debt){
  BigDecimal sum=amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
  if(sum.signum()<=0||debt.signum()<0||debt.compareTo(sum)>0||amounts.stream().anyMatch(x->x.signum()<0))throw new IllegalArgumentException("Некоректні суми перенесення боргу");
  debt=debt.setScale(2,RoundingMode.UNNECESSARY);
  var result=new ArrayList<BigDecimal>();BigDecimal allocated=BigDecimal.ZERO;
  for(var amount:amounts){var value=amount.multiply(debt).divide(sum,2,RoundingMode.DOWN);result.add(value);allocated=allocated.add(value);}
  int cents=debt.subtract(allocated).movePointRight(2).intValueExact();
  for(int i=0;cents>0;i=(i+1)%result.size())if(result.get(i).compareTo(amounts.get(i))<0){result.set(i,result.get(i).add(new BigDecimal("0.01")));cents--;}
  return result;
 }
}
