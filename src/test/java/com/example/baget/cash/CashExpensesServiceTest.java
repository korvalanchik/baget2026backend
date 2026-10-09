package com.example.baget.cash;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CashExpensesServiceTest {
 JdbcTemplate db;CashService cash;CashExpensesService service;Authentication auth;User admin;
 LocalDate day=LocalDate.now(CashService.CASH_ZONE);
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);cash=mock(CashService.class);service=new CashExpensesService(db,cash);auth=mock(Authentication.class);admin=new User();admin.setId(7L);admin.setUsername("accountant");var role=new Role();role.setName("ROLE_ADMIN");admin.setRoles(List.of(role));admin.setAllowedBranches(Set.of());when(cash.user(auth)).thenReturn(admin);when(cash.account(2L)).thenReturn(Map.of("id",2L,"branch_no",1L,"kind","BANK","opened_at",day.atStartOfDay()));when(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)).thenReturn(20L);}
 CashExpensesService.Spend spend(LocalDate date,String category,BigDecimal amount){return new CashExpensesService.Spend(UUID.randomUUID().toString(),2,date,category,amount,"Орендодавець","123","Оренда");}
 @Test void expenseUsesOnlyCashLedgerAndNotStockOrCustomerDebt(){service.spend(spend(day,"RENT",new BigDecimal("500")),auth);verify(cash).enough(2,new BigDecimal("500"));verify(db).update(startsWith("INSERT INTO ledger_entries"),eq(1L),eq("OUT"),eq(new BigDecimal("500")),eq(7L),eq(2L),eq(20L),eq("EXPENSE-20"),anyString());verify(db,never()).update(startsWith("UPDATE parts"),any(Object[].class));verify(db,never()).update(startsWith("INSERT INTO customer_transactions"),any(Object[].class));}
 @Test void insufficientCashDoesNotWrite(){doThrow(CashService.bad("Недостатньо коштів")).when(cash).enough(anyLong(),any());assertThrows(PreviewException.class,()->service.spend(spend(day,"RENT",BigDecimal.ONE),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void managerCannotSpendOrRead(){admin.getRoles().iterator().next().setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.spend(spend(day,"RENT",BigDecimal.ONE),auth));assertThrows(PreviewException.class,()->service.list(day,day,null,"",0,auth));verifyNoInteractions(db);}
 @Test void duplicateRequestReturnsWithoutWrites(){when(cash.prior(eq("EXPENSE"),anyString(),any(),eq(admin))).thenReturn(Map.of("id",20L));service.spend(spend(day,"RENT",BigDecimal.ONE),auth);verify(db,never()).update(anyString(),any(Object[].class));verify(cash,never()).enough(anyLong(),any());}
 @Test void invalidAmountsDatesAndPurchaseCategoryAreRejected(){assertThrows(PreviewException.class,()->service.spend(spend(day,"RENT",BigDecimal.ZERO),auth));assertThrows(PreviewException.class,()->service.spend(spend(day,"RENT",new BigDecimal("1.001")),auth));assertThrows(PreviewException.class,()->service.spend(spend(day,"DELIVERY",BigDecimal.ONE),auth));assertThrows(PreviewException.class,()->service.spend(spend(day.plusDays(1),"RENT",BigDecimal.ONE),auth));assertThrows(PreviewException.class,()->service.spend(spend(day.minusDays(1),"RENT",BigDecimal.ONE),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 void original(){when(db.queryForList(startsWith("SELECT e.*,l.amount"),eq(19L))).thenReturn(List.of(Map.of("cash_account_id",2L,"kind","EXPENSE","direction","OUT","category","RENT","payee","Owner","document_ref","123","amount",new BigDecimal("500"))));}
 @Test void reversalUsesOriginalLedgerAmountAndOriginalAccount(){original();service.reverse(19,new CashExpensesService.Reverse(UUID.randomUUID().toString(),"Помилковий запис"),auth);verify(db).update(startsWith("INSERT INTO ledger_entries"),eq(1L),eq("IN"),eq(new BigDecimal("500")),eq(7L),eq(2L),eq(20L),eq("EXPENSE-20"),anyString());verify(cash,never()).enough(anyLong(),any());}
 @Test void repeatedReversalCannotCreditTwice(){original();when(db.queryForList("SELECT id FROM cash_expenses WHERE reversal_of=?",19L)).thenReturn(List.of(Map.of("id",20L)));assertThrows(PreviewException.class,()->service.reverse(19,new CashExpensesService.Reverse(UUID.randomUUID().toString(),"Again"),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void transitCannotPay(){when(cash.account(2L)).thenReturn(Map.of("id",2L,"branch_no",1L,"kind","TRANSIT"));assertThrows(PreviewException.class,()->service.spend(spend(day,"RENT",BigDecimal.ONE),auth));verify(db,never()).update(anyString(),any(Object[].class));}
}
