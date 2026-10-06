package com.example.baget.costupdate;
import java.math.*;
public final class CostUpdateRules {
 private CostUpdateRules(){}
 public static BigDecimal calculate(BigDecimal base,BigDecimal rate){
  if(base==null||base.signum()<0||rate==null||rate.signum()<0||rate.compareTo(new BigDecimal("1000"))>0)throw new IllegalArgumentException("Некоректна собівартість або відсоток");
  var result=base.multiply(BigDecimal.ONE.add(rate.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP);
  if(result.compareTo(new BigDecimal("99999999.99"))>0)throw new IllegalArgumentException("Завелика розрахункова собівартість");
  return result;
 }
}
