package com.example.baget.reconciliation;

import com.example.baget.billing.BillingAccess;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.customer.CustomerTransactionRepository;
import com.example.baget.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PartnerReconciliationServiceTest {
 JdbcTemplate db;BillingAccess access;ReconciliationLedgerRepository ledger;CustomerTransactionRepository transactions;PartnerReconciliationService service;Authentication auth;User user;
 LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,3);
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(BillingAccess.class);ledger=mock(ReconciliationLedgerRepository.class);transactions=mock(CustomerTransactionRepository.class);auth=mock(Authentication.class);user=new User();user.setAllowedBranches(Set.of());
  when(access.user(auth)).thenReturn(user);when(access.admin(user)).thenReturn(true);
  when(db.queryForList(startsWith("SELECT p.id,p.name FROM"),eq(20L))).thenReturn(List.of(Map.of("id",20L,"name","Фірма")));
  when(ledger.balance(eq(20L),any())).thenReturn(new BigDecimal("100"));when(ledger.unallocated(eq(20L),any())).thenReturn(BigDecimal.ZERO);
  when(transactions.calculateAvailableAdvance(20L)).thenReturn(new BigDecimal("15"));
  service=new PartnerReconciliationService(db,access,ledger,transactions);
 }
 @Test void emptyPeriodPreservesBalanceAndShowsCurrentAdvanceSeparately(){
  var report=service.report(20,from,to,auth);
  assertEquals("Фірма",report.get("partnerName"));assertEquals(new BigDecimal("15"),report.get("availableAdvanceNow"));
  assertEquals(new BigDecimal("100"),((Map<?,?>)report.get("totals")).get("closing"));assertTrue(((List<?>)report.get("entries")).isEmpty());
  verify(ledger).entries(eq(20L),eq(ReconciliationMath.start(from)),eq(ReconciliationMath.start(to.plusDays(1))),any(Pageable.class));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void accountingRoleIsRequiredBeforeReadingAmounts(){when(access.user(auth)).thenThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Немає прав"));assertThrows(PreviewException.class,()->service.report(20,from,to,auth));verifyNoInteractions(ledger,transactions,db);}
 @Test void partialBranchAccessCannotProducePartialCompanyBalance(){
  when(access.admin(user)).thenReturn(false);
  when(db.queryForList(startsWith("SELECT branch_id FROM ledger_entries"),eq(Long.class),eq(20L),eq(20L),eq(20L),eq(20L),eq(20L))).thenReturn(List.of(2L));
  assertThrows(PreviewException.class,()->service.report(20,from,to,auth));verifyNoInteractions(ledger,transactions);
 }
 @Test void unknownCompanyDoesNotExposeCustomerBalance(){when(db.queryForList(startsWith("SELECT p.id,p.name FROM"),eq(20L))).thenReturn(List.of());assertThrows(PreviewException.class,()->service.report(20,from,to,auth));verifyNoInteractions(ledger,transactions);}
 @Test void invalidDatesDoNotQueryMoney(){assertThrows(PreviewException.class,()->service.report(20,to,from,auth));verifyNoInteractions(ledger,transactions);}
 @Test void oversizedPeriodDataIsRejectedWithoutTruncatedTotals(){when(ledger.entries(eq(20L),any(),any(),any(Pageable.class))).thenReturn(Collections.nCopies(10001,ReconciliationMathTest.entry("IN","PAYMENT_RECEIVED","1")));assertThrows(PreviewException.class,()->service.report(20,from,to,auth));verify(ledger,never()).balance(anyLong(),any());}
 @Test void paymentIsTakenFromLedgerWithoutReadingReferencePaymentTables(){
  when(ledger.entries(eq(20L),any(),any(),any(Pageable.class))).thenReturn(List.of(ReconciliationMathTest.entry("IN","PAYMENT_RECEIVED","30")));
  var report=service.report(20,from,to,auth);assertEquals(new BigDecimal("70"),((Map<?,?>)report.get("totals")).get("closing"));
  assertTrue(mockingDetails(db).getInvocations().stream().noneMatch(i->Arrays.toString(i.getArguments()).contains("partner_customer_payments")));
 }
 @Test void invoiceDetailDoesNotMultiplyPostedAmount(){
  var entry=new ReconciliationEntry(1L,java.time.OffsetDateTime.parse("2026-10-03T10:00:00Z"),1L,"Облік",com.example.baget.ledger.LedgerDirection.OUT,com.example.baget.ledger.LedgerCategory.INVOICE_ISSUED,new BigDecimal("20"),55L,"INV-55",null);
  when(ledger.entries(eq(20L),any(),any(),any(Pageable.class))).thenReturn(List.of(entry));
  when(ledger.invoiceBalances(eq(20L),any(),eq(List.of(55L)))).thenReturn(Collections.singletonList(new Object[]{55L,new BigDecimal("20")}));
  when(db.queryForList(startsWith("SELECT i.id,i.invoice_no"),any(Object[].class))).thenReturn(List.of(new HashMap<>(Map.of("id",55L,"invoiceNo",555L,"amount",new BigDecimal("20"),"commercialOwner",20L))));
  when(db.queryForList(startsWith("SELECT ip.invoice_id"),any(Object[].class))).thenReturn(List.of(Map.of("invoiceId",55L,"productId",1L,"amount",BigDecimal.TEN),Map.of("invoiceId",55L,"productId",2L,"amount",BigDecimal.TEN)));
  var report=service.report(20,from,to,auth);assertEquals(new BigDecimal("120"),((Map<?,?>)report.get("totals")).get("closing"));
  var invoice=(Map<?,?>)((List<?>)report.get("invoices")).get(0);assertEquals(2,((List<?>)invoice.get("lines")).size());
 }
}
