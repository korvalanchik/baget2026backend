package com.example.baget.finance;
import java.math.BigDecimal;
import java.time.LocalDate;
public final class CreditRules {
 private CreditRules() {}
 public static boolean canIssue(boolean active,BigDecimal debt,boolean allowed,LocalDate dueDate) {
  return active && (debt.signum()==0 || (allowed && dueDate!=null));
 }
 public static boolean overdue(BigDecimal debt,LocalDate dueDate,LocalDate today) {
  return debt.signum()>0 && dueDate!=null && dueDate.isBefore(today);
 }
}
