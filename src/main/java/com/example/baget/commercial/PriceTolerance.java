package com.example.baget.commercial;
import java.math.BigDecimal;
public final class PriceTolerance {
 private PriceTolerance() {}
 public static boolean requiresApproval(BigDecimal baseline,BigDecimal actual) {
  if(baseline==null || actual==null || baseline.signum()<0 || actual.signum()<0)throw new IllegalArgumentException("Некоректні суми");
  return actual.subtract(baseline).abs().multiply(new BigDecimal("100")).compareTo(baseline.multiply(new BigDecimal("5")))>0;
 }
}
