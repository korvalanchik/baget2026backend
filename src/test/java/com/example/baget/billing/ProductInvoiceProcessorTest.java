package com.example.baget.billing;
import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.ledger.*;
import com.example.baget.branch.*;
import com.example.baget.finance.PaymentOrchestrator;
import com.example.baget.users.User;
import com.example.baget.util.InvoiceServiceUtil;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProductInvoiceProcessorTest {
 com.example.baget.finance.InvoiceSettlementService settlement;LedgerRepository ledger;JdbcTemplate db;BillingAccess access;InvoiceRepository invoices;CustomerTransactionRepository transactions;PaymentOrchestrator orchestrator;ProductInvoiceProcessor service;Authentication auth;User user;Map<String,Object> product;
 ProductInvoiceProcessor.Issue request(){return new ProductInvoiceProcessor.Issue("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",7,1,List.of(new ProductInvoiceProcessor.Selected(10,4)),"Тест");}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(BillingAccess.class);invoices=mock(InvoiceRepository.class);transactions=mock(CustomerTransactionRepository.class);orchestrator=mock(PaymentOrchestrator.class);
  var customers=mock(CustomerRepository.class);var branches=mock(BranchRepository.class);var numbering=mock(InvoiceServiceUtil.class);
  user=new User();user.setUsername("accountant");auth=mock(Authentication.class);when(access.user(auth)).thenReturn(user);
  var customer=new Customer();customer.setCustNo(7L);when(customers.findById(7L)).thenReturn(Optional.of(customer));
  var branch=new Branch();when(branches.findById(1L)).thenReturn(Optional.of(branch));when(numbering.generateTodayCode()).thenReturn(26092026001L);
  when(invoices.saveAndFlush(any())).thenAnswer(i->{Invoice inv=i.getArgument(0);inv.setId(42L);return inv;});
  when(transactions.saveAndFlush(any())).thenAnswer(i->{CustomerTransaction tx=i.getArgument(0);tx.setId(99L);return tx;});
  ledger=mock(LedgerRepository.class);settlement=mock(com.example.baget.finance.InvoiceSettlementService.class);
  service=spy(new ProductInvoiceProcessor(db,access,invoices,customers,branches,transactions,ledger,orchestrator,numbering,mock(EntityManager.class),new ObjectMapper(),settlement));
  doReturn(Map.of("id",42L)).when(service).read(42,auth);
  when(db.queryForList("SELECT order_no FROM order_products WHERE id=? AND active=1",Long.class,10L)).thenReturn(List.of(1L));
  when(db.queryForObject("SELECT COUNT(*) FROM product_invoice_claims WHERE product_id=?",Long.class,10L)).thenReturn(0L);
  when(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,1L)).thenReturn(0L);
  product=new HashMap<>(Map.of("id",10L,"order_no",1L,"branchNo",1L,"active",true,"production_state","COMPLETED","warehouse_received_at","2026-09-26","confirmed_revision",4L,"actual_total",new BigDecimal("100.00"),"confirmed_calculation","{\"productId\":10,\"revision\":4,\"total\":100.00}"));
  product.put("production_confirmed",true);
  when(db.queryForMap(startsWith("SELECT p.*,o.BranchNo"),eq(10L))).thenReturn(product);
 }
 @Test void notReceivedCannotBeBilled(){product.put("warehouse_received_at",null);assertThrows(PreviewException.class,()->service.issue(request(),auth));verifyNoInteractions(orchestrator);}
 @Test void alreadyClaimedCannotCreateSecondDebt(){when(db.queryForObject("SELECT COUNT(*) FROM product_invoice_claims WHERE product_id=?",Long.class,10L)).thenReturn(1L);assertThrows(PreviewException.class,()->service.issue(request(),auth));verifyNoInteractions(orchestrator);}
 @Test void oldWholeOrderInvoiceBlocksPartialBilling(){when(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,1L)).thenReturn(1L);assertThrows(PreviewException.class,()->service.issue(request(),auth));verifyNoInteractions(orchestrator);}
 @Test void snapshotMismatchCannotChangeDebt(){product.put("actual_total",new BigDecimal("200.00"));assertThrows(PreviewException.class,()->service.issue(request(),auth));verifyNoInteractions(orchestrator);}
 @Test void successfulIssueClaimsOneProductAndPostsOnlyItsConfirmedAmount(){service.issue(request(),auth);verify(db).update("INSERT INTO product_invoice_claims(product_id,invoice_id) VALUES(?,?)",10L,42L);var captor=org.mockito.ArgumentCaptor.forClass(CustomerTransaction.class);verify(transactions).saveAndFlush(captor.capture());assertEquals(new BigDecimal("-100.00"),captor.getValue().getAmount());assertNull(captor.getValue().getOrder());assertEquals(InvoiceEnums.InvoiceType.CONSOLIDATED,captor.getValue().getInvoice().getType());verify(orchestrator).postInvoiceTransactions(anyList(),eq(user),any(Customer.class));}
 @Test void mergeTransfersOnlyLedgerDebtAndKeepsSources() {
  var c=new Customer();c.setCustNo(7L);var branch=new Branch();branch.setBranchNo(1L);
  var sources=new ArrayList<Invoice>();
  for(long id:List.of(11L,12L)){
   var gross=new BigDecimal(id==11?"100.00":"200.00");var debt=new BigDecimal(id==11?"40.00":"120.00");
   var inv=Invoice.builder().id(id).invoiceNo(id).customer(c).totalAmount(gross).status(InvoiceEnums.InvoiceStatus.PARTIALLY_PAID).lifecycle(InvoiceEnums.InvoiceLifecycle.ACTIVE).build();sources.add(inv);
   when(db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,id)).thenReturn(7L);
   when(invoices.findById(id)).thenReturn(Optional.of(inv));when(ledger.calculateInvoiceDebt(id)).thenReturn(debt);
   when(ledger.findTopByInvoiceIdAndDirectionAndCategoryInOrderByCreatedAtDescIdDesc(eq(id),eq(LedgerDirection.OUT),anyList())).thenReturn(Optional.of(LedgerEntry.builder().branch(branch).build()));
   when(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,id)).thenReturn(1L);
   when(db.queryForList("SELECT * FROM invoice_products WHERE invoice_id=? ORDER BY product_id",id)).thenReturn(List.of(Map.of("product_id",id,"order_no",1L,"confirmed_revision",4L,"snapshot","{}","original_amount",gross,"billed_amount",gross)));
  }
  when(db.update(eq("UPDATE product_invoice_claims SET invoice_id=? WHERE product_id=? AND invoice_id=?"),eq(42L),any(),any())).thenReturn(1);
  service.merge(new ProductInvoiceProcessor.Merge("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",7,1,List.of(11L,12L),"Залишки"),auth);
  var tx=org.mockito.ArgumentCaptor.forClass(CustomerTransaction.class);verify(transactions,times(3)).saveAndFlush(tx.capture());
  assertEquals(List.of(new BigDecimal("40.00"),new BigDecimal("120.00"),new BigDecimal("-160.00")),tx.getAllValues().stream().map(CustomerTransaction::getAmount).toList());
  assertTrue(sources.stream().allMatch(inv->inv.getLifecycle()==InvoiceEnums.InvoiceLifecycle.MERGED));
  assertEquals(new BigDecimal("160.00"),tx.getAllValues().get(2).getInvoice().getTotalAmount());
  verify(orchestrator).postInvoiceTransactions(anyList(),eq(user),any(Customer.class));
 }
 @Test void staleDebtCannotPostPayment() {
  when(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,42L)).thenReturn(1L);
  when(db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,42L)).thenReturn(7L);
  when(ledger.calculateInvoiceDebt(42L)).thenReturn(new BigDecimal("60.00"));
  var r=new ProductInvoiceProcessor.Payment("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",new BigDecimal("20.00"),BigDecimal.ZERO,new BigDecimal("100.00"),"");
  assertThrows(PreviewException.class,()->service.pay(42,r,auth));verifyNoInteractions(settlement);
 }
 @Test void paymentDelegatesToExistingSettlement() {
  when(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,42L)).thenReturn(1L);
  when(db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,42L)).thenReturn(7L);
  when(ledger.calculateInvoiceDebt(42L)).thenReturn(new BigDecimal("100.00"));
  var r=new ProductInvoiceProcessor.Payment("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",new BigDecimal("20.00"),new BigDecimal("10.00"),new BigDecimal("100.00"),"Оплата");
  service.pay(42,r,auth);
  verify(settlement).settleInvoice(new InvoicePaymentRequest(null,42L,null,new BigDecimal("20.00"),new BigDecimal("10.00"),"Оплата"),auth);
 }
}
