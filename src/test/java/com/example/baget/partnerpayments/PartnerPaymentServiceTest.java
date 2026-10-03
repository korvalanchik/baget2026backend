package com.example.baget.partnerpayments;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.users.User;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PartnerPaymentServiceTest {
 JdbcTemplate db;CustomerPaymentAccess access;PartnerPaymentService service;Authentication auth;User user;
 ObjectMapper json=new ObjectMapper();Map<String,Object> order;
 final String key="c30b2f11-8dbf-47bd-91f4-317f40ad1af2";
 BigDecimal n(String v){return new BigDecimal(v);}
 PartnerPaymentService.Payment request(Long target,String cash,String allocation){return new PartnerPaymentService.Payment(key,1,20,100L,target,n(cash),n(allocation),target==null?null:n("200"),target==null?null:n("50"),"Запис");}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(CustomerPaymentAccess.class);auth=mock(Authentication.class);user=new User();
  when(auth.getName()).thenReturn("manager");when(access.user(auth)).thenReturn(user);
  service=spy(new PartnerPaymentService(db,access,json));
  doReturn(new HashMap<>(Map.of("customerId",10L,"branchNo",1L,"partnerId",20L,"mode","RESELLER"))).when(service).context(100L,10L,1L,auth);
  when(db.queryForObject(startsWith("SELECT COALESCE(SUM(CASE"),eq(BigDecimal.class),eq(10L),eq(20L))).thenReturn(n("100"));
  order=new HashMap<>(Map.of("orderNo",100L,"branchNo",1L,"status",3,"total",n("200"),"paid",n("50"),"pricePending",0L,"unfinished",0L,"incomplete",0L));
  when(db.queryForList(startsWith("SELECT o.OrderNo AS orderNo"),eq(10L),eq(20L),eq(100L))).thenAnswer(i->List.of(new HashMap<>(order)));
 }
 @Test void advanceBelongsToCompanyAndCustomerNotOrder(){
  var result=service.pay(10,request(null,"30","0"),auth);
  assertEquals(0,n("130").compareTo(result.path("availableAdvance").decimalValue()));
  verify(db).update(startsWith("INSERT INTO partner_customer_payments"),eq(key),eq(20L),eq(10L),eq(1L),isNull(),eq("ADVANCE"),eq(n("30")),eq(n("0")),eq("manager"),eq("Запис"),anyString(),anyString());
 }
 @Test void allocationAndCashPayOnlyRetailDebt(){
  var result=service.pay(10,request(100L,"100","50"),auth);
  assertEquals(0,result.path("remainingDebt").decimalValue().signum());
  assertEquals(0,n("50").compareTo(result.path("availableAdvance").decimalValue()));
  verify(access).branch(user,1L);
 }
 @Test void partialPaymentKeepsRemainingDebt(){
  assertEquals(0,n("120").compareTo(service.pay(10,request(100L,"30","0"),auth).path("remainingDebt").decimalValue()));
 }
 @Test void retryDoesNotWriteTwice() throws Exception {
  var r=request(100L,"100","50");
  when(db.queryForList(startsWith("SELECT customer_id,partner_id,actor,payload,result"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"partner_id",20L,"actor","manager","payload",json.writeValueAsString(r),"result","{\"ok\":true}")));
  assertTrue(service.pay(10,r,auth).path("ok").asBoolean());verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void changedRetryPayloadIsRejected() throws Exception {
  var r=request(100L,"100","50");
  when(db.queryForList(startsWith("SELECT customer_id,partner_id,actor,payload,result"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"partner_id",20L,"actor","manager","payload",json.writeValueAsString(r),"result","{}")));
  assertThrows(PreviewException.class,()->service.pay(10,request(100L,"99","50"),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void allocationCannotSpendBalanceConsumedByAnotherBranch(){
  when(db.queryForObject(startsWith("SELECT COALESCE(SUM(CASE"),eq(BigDecimal.class),eq(10L),eq(20L))).thenReturn(n("10"));
  assertThrows(PreviewException.class,()->service.pay(10,request(100L,"100","50"),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void cancelledOrPendingPriceOrderCannotBePaid(){
  order.put("status",5);assertThrows(PreviewException.class,()->service.pay(10,request(100L,"10","0"),auth));
  order.put("status",3);order.put("pricePending",1L);assertThrows(PreviewException.class,()->service.pay(10,request(100L,"10","0"),auth));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void priceChangeRequiresRefresh(){order.put("total",n("220"));assertThrows(PreviewException.class,()->service.pay(10,request(100L,"10","0"),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void anotherCompanyOrderCannotBePaid(){
  when(db.queryForList(startsWith("SELECT o.OrderNo AS orderNo"),eq(10L),eq(20L),eq(100L))).thenReturn(List.of());
  assertThrows(PreviewException.class,()->service.pay(10,request(100L,"10","0"),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void changedContextCannotMoveFundsToOtherCompany(){
  doReturn(Map.of("customerId",10L,"branchNo",1L,"partnerId",21L,"mode","RESELLER")).when(service).context(100L,10L,1L,auth);
  assertThrows(PreviewException.class,()->service.pay(10,request(null,"10","0"),auth));verifyNoInteractions(db);
 }
 @Test void accessDeniedBeforeFinancialWrites(){doThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Немає доступу")).when(access).user(auth);assertThrows(PreviewException.class,()->service.pay(10,request(null,"10","0"),auth));verifyNoInteractions(db);}
 @Test void savedOrderDeterminesCompanyRatherThanCurrentBranchSettings(){
  var real=new PartnerPaymentService(db,access,json);
  when(db.queryForMap(startsWith("SELECT CustNo AS customerId"),eq(100L))).thenReturn(new HashMap<>(Map.of("customerId",10L,"branchNo",1L,"partnerId",20L,"mode","RESELLER")));
  when(db.queryForObject("SELECT Company FROM customer WHERE CustNo=?",String.class,20L)).thenReturn("Посередник");
  assertEquals(20L,real.context(100L,10L,1L,auth).get("partnerId"));verify(access).branch(user,1L);
 }
 @Test void orderContextCannotBeUsedForAnotherCustomer(){
  var real=new PartnerPaymentService(db,access,json);
  when(db.queryForMap(startsWith("SELECT CustNo AS customerId"),eq(100L))).thenReturn(new HashMap<>(Map.of("customerId",10L,"branchNo",1L,"partnerId",20L,"mode","RESELLER")));
  assertThrows(PreviewException.class,()->real.context(100L,11L,1L,auth));
 }
 @Test void amountScaleDoesNotBreakIdempotency(){assertTrue(PartnerPaymentService.same(request(100L,"10","0"),request(100L,"10.00","0.00")));}
}
