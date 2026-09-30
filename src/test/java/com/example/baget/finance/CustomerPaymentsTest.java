package com.example.baget.finance;
import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.users.User;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CustomerPaymentsTest {
 JdbcTemplate db; CustomerPaymentAccess access; InvoiceSettlementService settlement; InvoiceRepository invoices; InvoiceFinanceService finance; CustomerPayments service;
 Authentication auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 String key="a70b4dd2-2b21-4089-97ea-0b7093c2a310";
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(CustomerPaymentAccess.class);settlement=mock(InvoiceSettlementService.class);invoices=mock(InvoiceRepository.class);finance=mock(InvoiceFinanceService.class);
  when(access.user(auth)).thenReturn(new User());
  service=new CustomerPayments(access,mock(CustomerPaymentService.class),settlement,invoices,finance,db,json);
 }
 CustomerPayments.Payment request(Long invoice,String amount,String allocation){return new CustomerPayments.Payment(key,1,invoice,new BigDecimal(amount),new BigDecimal(allocation),"Тест");}
 @Test void advanceRemainsUnlinkedToOrderOrInvoice(){
  var r=request(null,"500","0");when(settlement.settleAdvance(any(),eq(auth))).thenReturn(List.of());service.pay(10,r,auth);
  verify(settlement).settleAdvance(new InvoicePaymentRequest(1L,null,10L,new BigDecimal("500"),BigDecimal.ZERO,"Тест"),auth);
  verify(settlement,never()).settleInvoice(any(),any());
  verify(db).update(startsWith("INSERT INTO customer_payment_requests"),eq(key),eq(10L),eq(1L),eq("manager"),anyString(),eq("[]"));
 }
 @Test void lostResponseRetryDoesNotPostAgain() throws Exception {
  var r=request(null,"500.00","0.00");
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","payload",json.writeValueAsString(request(null,"500","0")),"result","[{\"id\":7}]")));
  assertEquals(7,service.pay(10,r,auth).get(0).get("id").asInt());verifyNoInteractions(settlement);
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void changedRequestKeyPayloadRejected() throws Exception {
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","payload",json.writeValueAsString(request(null,"500","0")),"result","[]")));
  assertThrows(PreviewException.class,()->service.pay(10,request(null,"501","0"),auth));verifyNoInteractions(settlement);
 }
 @Test void partialAdvanceAndCashUseExistingSettlement(){
  var c=new Customer();c.setCustNo(10L);var i=new Invoice();i.setCustomer(c);
  when(invoices.findById(5L)).thenReturn(Optional.of(i));when(finance.calculateInvoiceDebt(i)).thenReturn(new BigDecimal("1000"));
  when(settlement.settleInvoice(any(),eq(auth))).thenReturn(List.of());
  service.pay(10,request(5L,"100","200"),auth);
  verify(settlement).settleInvoice(new InvoicePaymentRequest(1L,5L,10L,new BigDecimal("100"),new BigDecimal("200"),"Тест"),auth);
 }
 @Test void changedDebtRejectsOverpaymentBeforePosting(){
  var c=new Customer();c.setCustNo(10L);var i=new Invoice();i.setCustomer(c);
  when(invoices.findById(5L)).thenReturn(Optional.of(i));when(finance.calculateInvoiceDebt(i)).thenReturn(new BigDecimal("50"));
  assertThrows(PreviewException.class,()->service.pay(10,request(5L,"100","0"),auth));verifyNoInteractions(settlement);
 }
 @Test void anotherCustomerCannotSpendThisAdvance(){
  var c=new Customer();c.setCustNo(20L);var i=new Invoice();i.setCustomer(c);when(invoices.findById(5L)).thenReturn(Optional.of(i));
  assertThrows(PreviewException.class,()->service.pay(10,request(5L,"0","100"),auth));verifyNoInteractions(settlement);
 }
 @Test void accessCheckedBeforeLockOrWrites(){
  doThrow(new IllegalStateException("denied")).when(access).customer(any(),eq(10L),eq(1L));
  assertThrows(IllegalStateException.class,()->service.pay(10,request(null,"500","0"),auth));verifyNoInteractions(db,settlement);
 }
 @Test void cannotAllocateWithoutInvoice(){
  assertThrows(PreviewException.class,()->service.pay(10,request(null,"0","100"),auth));verifyNoInteractions(settlement);
 }
}
