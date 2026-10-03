package com.example.baget.partnerpayments;
import java.math.BigDecimal;
/** Pure money checks, also used by the service after locking the customer's account. */
public final class PartnerPaymentRules {
 private PartnerPaymentRules() {}
 public static void money(BigDecimal value) {
  if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>2 || value.compareTo(new BigDecimal("999999999.99"))>0)
   throw new IllegalArgumentException("Вкажіть невід’ємну суму до двох знаків після коми");
 }
 public static void validate(BigDecimal cash,BigDecimal allocation,BigDecimal available,BigDecimal debt) {
  money(cash);money(allocation);
  if(cash.add(allocation).signum()<=0)throw new IllegalArgumentException("Вкажіть суму коштів або авансу");
  if(allocation.compareTo(available)>0)throw new IllegalArgumentException("Недостатньо авансу у цієї фірми. Оновіть дані");
  if(debt==null && allocation.signum()!=0)throw new IllegalArgumentException("Аванс можна використати лише для оплати замовлення");
  if(debt!=null && cash.add(allocation).compareTo(debt)>0)throw new IllegalArgumentException("Сума перевищує борг. Надлишок прийміть окремим авансом");
 }
 public record Deltas(BigDecimal cash,BigDecimal advance,BigDecimal paid) {}
 public static Deltas reverse(boolean advance,BigDecimal cash,BigDecimal allocation,BigDecimal balance,BigDecimal paid) {
  money(cash);money(allocation);
  if(advance){
   if(cash.compareTo(balance)>0)throw new IllegalArgumentException("Цей аванс уже використано. Спочатку скасуйте помилкову оплату, що використала аванс");
   return new Deltas(cash.negate(),cash.negate(),BigDecimal.ZERO);
  }
  BigDecimal applied=cash.add(allocation);
  if(paid==null || applied.compareTo(paid)>0)throw new IllegalArgumentException("Частину оплати вже перенесено в аванс. Скасування призведе до від’ємної оплати замовлення");
  return new Deltas(cash.negate(),allocation,applied.negate());
 }
 public static Deltas transfer(BigDecimal amount,BigDecimal total,BigDecimal paid) {
  money(amount);
  if(amount.signum()<=0 || total==null || amount.compareTo(paid.subtract(total))>0)
   throw new IllegalArgumentException("Сума перенесення перевищує підтверджену переплату");
  return new Deltas(BigDecimal.ZERO,amount,amount.negate());
 }
}
