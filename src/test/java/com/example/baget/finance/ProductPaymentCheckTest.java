package com.example.baget.finance;
import com.example.baget.invoices.*;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProductPaymentCheckTest {
 CustomerCreditService credit;
 JdbcTemplate db;InvoiceRepository invoices;InvoiceFinanceService finance;EntityManager em;ProductPaymentCheck service;Invoice invoice;
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);invoices=mock(InvoiceRepository.class);finance=mock(InvoiceFinanceService.class);em=mock(EntityManager.class);
  service=new ProductPaymentCheck(db,invoices,finance,em);
  credit=mock(CustomerCreditService.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"credit",credit);
  when(db.queryForObject(startsWith("SELECT o.BranchNo"),eq(Long.class),eq(14L))).thenReturn(1L);
  when(credit.policy(anyLong(),anyLong())).thenReturn(Map.of("allowed",false,"revision",0L));
  when(credit.terms(anyLong())).thenReturn(Map.of("revision",0L));
  var customer=new com.example.baget.customer.Customer();customer.setCustNo(10L);
  invoice=new Invoice();invoice.setCustomer(customer);invoice.setLifecycle(InvoiceEnums.InvoiceLifecycle.ACTIVE);invoice.setStatus(InvoiceEnums.InvoiceStatus.ISSUED);
  when(db.queryForList("SELECT invoice_id FROM product_invoice_claims WHERE product_id=?",Long.class,14L)).thenReturn(List.of(5L));
  when(invoices.findById(5L)).thenReturn(Optional.of(invoice));when(finance.calculateInvoiceDebt(invoice)).thenReturn(BigDecimal.ZERO);
 }
 @Test void resellerDeliveryDoesNotDependOnCompanyInvoicePayment() {
  when(db.queryForObject(startsWith("SELECT o.commerce_mode"),eq(String.class),eq(14L))).thenReturn("RESELLER");
  assertNull(service.lock(14));assertEquals(Map.of(),service.checkIssue(14,null));
  verifyNoInteractions(finance,credit,invoices,em);
 }
 @Test void unpaidCannotBeIssued(){when(finance.calculateInvoiceDebt(invoice)).thenReturn(BigDecimal.ONE);assertThrows(PreviewException.class,()->service.requirePaid(14,5L));}
 @Test void paidInvoiceCanBeIssued(){assertDoesNotThrow(()->service.requirePaid(14,5L));verify(em).refresh(invoice);}
 @Test void statusAloneDoesNotProvePayment(){invoice.setStatus(InvoiceEnums.InvoiceStatus.PAID);when(finance.calculateInvoiceDebt(invoice)).thenReturn(BigDecimal.ONE);assertThrows(PreviewException.class,()->service.requirePaid(14,5L));}
 @Test void mergedInvoiceCannotBeIssued(){invoice.setLifecycle(InvoiceEnums.InvoiceLifecycle.MERGED);assertThrows(PreviewException.class,()->service.requirePaid(14,5L));}
 @Test void draftCannotBeIssued(){invoice.setStatus(InvoiceEnums.InvoiceStatus.DRAFT);assertThrows(PreviewException.class,()->service.requirePaid(14,5L));}
 @Test void invoiceChangedDuringLockRequiresReload(){assertThrows(PreviewException.class,()->service.requirePaid(14,6L));verifyNoInteractions(finance);}
 @Test void missingInvoiceCannotBeIssued(){when(db.queryForList("SELECT invoice_id FROM product_invoice_claims WHERE product_id=?",Long.class,14L)).thenReturn(List.of());assertThrows(PreviewException.class,()->service.lock(14));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void legacyWholeOrderInvoiceCanBeUsed(){
  when(db.queryForList("SELECT invoice_id FROM product_invoice_claims WHERE product_id=?",Long.class,14L)).thenReturn(List.of());
  when(db.queryForList(startsWith("SELECT DISTINCT i.id"),eq(Long.class),eq(14L))).thenReturn(List.of(5L));
  assertDoesNotThrow(()->service.requirePaid(14,5L));
 }
 @Test void ambiguousLegacyInvoicesBlockIssue(){
  when(db.queryForList("SELECT invoice_id FROM product_invoice_claims WHERE product_id=?",Long.class,14L)).thenReturn(List.of());
  when(db.queryForList(startsWith("SELECT DISTINCT i.id"),eq(Long.class),eq(14L))).thenReturn(List.of(5L,6L));
  assertThrows(PreviewException.class,()->service.lock(14));
 }
 @Test void explicitPermissionAndDateAllowDebtAndCaptureSnapshot(){
  when(finance.calculateInvoiceDebt(invoice)).thenReturn(new BigDecimal("300"));
  when(credit.policy(10,1)).thenReturn(Map.of("allowed",true,"revision",2L));
  when(credit.terms(5)).thenReturn(Map.of("dueDate",java.time.LocalDate.of(2026,10,10),"revision",3L));
  var result=service.checkIssue(14,5L);assertEquals(new BigDecimal("300"),result.get("debt"));assertEquals(2L,result.get("policyRevision"));
  service.recordIssue(14,7,true,"manager",result);
  verify(db).update(startsWith("INSERT INTO product_credit_issues"),eq(14L),eq(5L),eq(10L),eq(1L),eq(7L),eq("ACCEPT"),eq(new BigDecimal("300")),eq(java.time.LocalDate.of(2026,10,10)),eq(2L),eq(3L),eq("manager"));
 }
 @Test void permissionWithoutDateDoesNotAllowDebt(){when(finance.calculateInvoiceDebt(invoice)).thenReturn(BigDecimal.ONE);when(credit.policy(10,1)).thenReturn(Map.of("allowed",true,"revision",2L));assertThrows(PreviewException.class,()->service.checkIssue(14,5L));}
}
