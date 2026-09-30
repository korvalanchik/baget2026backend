package com.example.baget.finance;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class CreditRulesTest {
 final LocalDate today=LocalDate.of(2026,9,30);
 @Test void paidDoesNotNeedPermissionOrDate(){assertTrue(CreditRules.canIssue(true,BigDecimal.ZERO,false,null));}
 @Test void bothPermissionAndDateRequired(){assertFalse(CreditRules.canIssue(true,BigDecimal.ONE,false,today));assertFalse(CreditRules.canIssue(true,BigDecimal.ONE,true,null));assertTrue(CreditRules.canIssue(true,BigDecimal.ONE,true,today));}
 @Test void inactiveInvoiceNeverAllowsIssue(){assertFalse(CreditRules.canIssue(false,BigDecimal.ZERO,true,today));assertFalse(CreditRules.canIssue(false,BigDecimal.ONE,true,today));}
 @Test void dueTodayIsNotOverdue(){assertFalse(CreditRules.overdue(BigDecimal.ONE,today,today));assertTrue(CreditRules.overdue(BigDecimal.ONE,today.minusDays(1),today));}
 @Test void paidAndUndatedAreNotOverdue(){assertFalse(CreditRules.overdue(BigDecimal.ZERO,today.minusDays(1),today));assertFalse(CreditRules.overdue(BigDecimal.ONE,null,today));}
}
