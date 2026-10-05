package com.example.baget.payroll;
import java.math.*;
public final class RewardMath {
 private RewardMath(){}
 public static BigDecimal calculate(BigDecimal base,BigDecimal rate){
  if(base==null||rate==null||base.signum()<0||rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Некоректна база або відсоток");
  return base.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
 }
}
