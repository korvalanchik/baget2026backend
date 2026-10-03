package com.example.baget.partnerpayments;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerPaymentAdjustmentTest {
 JdbcTemplate db; CustomerPaymentAccess access; PartnerPaymentService service; Authentication auth; User user;
 final ObjectMapper json=new ObjectMapper();
 final String key="41879f38-980b-48d3-b145-73ce2c4b0d19";
 Map<String,Object> order,payment;
 static BigDecimal n(String value){return new BigDecimal(value);}
 PartnerPaymentService.Adjustment reverse(){return new PartnerPaymentService.Adjustment(key,1,20,100L,PartnerPaymentService.AdjustmentKind.REVERSAL,7L,null,n("0"),null,null,"Помилковий запис");}
 PartnerPaymentService.Adjustment transfer(){return new PartnerPaymentService.Adjustment(key,1,20,100L,PartnerPaymentService.AdjustmentKind.TRANSFER,null,100L,n("100"),n("900"),n("1000"),"Ціна зменшилася після обміру");}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(CustomerPaymentAccess.class);auth=mock(Authentication.class);user=new User();
  Role role=new Role();role.setName("ROLE_ACCOUNTANT");user.setRoles(List.of(role));
  when(auth.getName()).thenReturn("accountant");when(access.user(auth)).thenReturn(user);
  service=spy(new PartnerPaymentService(db,access,json));
  doReturn(Map.of("mode","RESELLER","partnerId",20L,"customerId",10L,"branchNo",1L)).when(service).context(100L,10L,1L,auth);
  when(db.queryForObject("SELECT COALESCE(SUM(advance_delta),0) FROM partner_customer_movements WHERE customer_id=? AND partner_id=?",BigDecimal.class,10L,20L)).thenReturn(n("200"));
  order=new HashMap<>(Map.of("orderNo",100L,"branchNo",1L,"status",3,"total",n("900"),"paid",n("1000"),"pricePending",0L,"unfinished",0L,"incomplete",0L));
  when(db.queryForList(startsWith("SELECT o.OrderNo AS orderNo"),eq(10L),eq(20L),eq(100L))).thenAnswer(i->List.of(new HashMap<>(order)));
  payment=new HashMap<>(Map.of("id",7L,"branch_no",1L,"order_no",100L,"operation","PAYMENT","amount",n("200"),"allocation_amount",n("400")));
  when(db.queryForList(startsWith("SELECT id,branch_no,order_no,operation"),eq(7L),eq(10L),eq(20L))).thenAnswer(i->List.of(new HashMap<>(payment)));
 }
 void noWrites(){verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void reversePaymentRestoresAllocatedAdvanceAndRemovesCashAndPaid(){
  var result=service.adjust(10,reverse(),auth);
  assertEquals(0,n("600").compareTo(result.path("availableAdvance").decimalValue()));
  assertEquals(0,n("-600").compareTo(result.path("paidDelta").decimalValue()));
  assertEquals(0,n("-200").compareTo(result.path("cashDelta").decimalValue()));
  verify(db).update(startsWith("INSERT INTO partner_payment_adjustments"),eq(key),eq(20L),eq(10L),eq(1L),eq(100L),eq("REVERSAL"),eq(7L),eq(n("-200")),eq(n("400")),eq(n("-600")),eq("accountant"),eq("Помилковий запис"),anyString(),anyString());
  assertEquals(1,mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("update")).count());
 }
 @Test void reverseUnusedAdvanceReducesOnlyWallet(){
  payment.put("operation","ADVANCE");payment.put("order_no",null);payment.put("allocation_amount",n("0"));
  var result=service.adjust(10,reverse(),auth);
  assertEquals(0,result.path("availableAdvance").decimalValue().signum());
  assertEquals(0,result.path("paidDelta").decimalValue().signum());assertTrue(result.path("orderNo").isNull());
 }
 @Test void spentAdvanceCannotBeReversed(){
  payment.put("operation","ADVANCE");payment.put("order_no",null);payment.put("allocation_amount",n("0"));payment.put("amount",n("201"));
  assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();
 }
 @Test void reversalAfterTransferCannotMakeOrderPaidNegative(){order.put("paid",n("599"));assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();}
 @Test void alreadyReversedIsRejectedEvenWithANewRequestKey(){
  when(db.queryForList("SELECT id FROM partner_payment_adjustments WHERE source_payment_id=?",7L)).thenReturn(List.of(Map.of("id",8L)));
  assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();
 }
 @Test void retryReturnsOriginalResultBeforeCheckingCurrentBalancesOrSource(){
  var r=reverse();stored(r,"accountant");
  assertTrue(service.adjust(10,r,auth).path("ok").asBoolean());noWrites();
  verify(db,never()).queryForObject(startsWith("SELECT COALESCE"),eq(BigDecimal.class),anyLong(),anyLong());
 }
 void stored(PartnerPaymentService.Adjustment r,String actor){
  try{when(db.queryForList(startsWith("SELECT customer_id,partner_id,actor,payload,result FROM partner_payment_adjustments"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"partner_id",20L,"actor",actor,"payload",json.writeValueAsString(r),"result","{\"ok\":true}")));}catch(Exception e){throw new AssertionError(e);}
 }
 @Test void requestKeyCannotBeReusedForAnotherOperation(){stored(reverse(),"accountant");assertThrows(PreviewException.class,()->service.adjust(10,transfer(),auth));noWrites();}
 @Test void requestKeyCannotBeReusedByAnotherActor(){stored(reverse(),"other");assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();}
 @Test void confirmedOverpaymentMovesToSamePartnerWallet(){
  var result=service.adjust(10,transfer(),auth);
  assertEquals(0,n("300").compareTo(result.path("availableAdvance").decimalValue()));
  assertEquals(0,n("-100").compareTo(result.path("paidDelta").decimalValue()));
  assertEquals(0,result.path("cashDelta").decimalValue().signum());assertEquals(20,result.path("partnerId").asLong());
 }
 @Test void changedPriceOrPaymentRequiresRefresh(){
  order.put("total",n("850"));assertThrows(PreviewException.class,()->service.adjust(10,transfer(),auth));
  order.put("total",n("900"));order.put("paid",n("1100"));assertThrows(PreviewException.class,()->service.adjust(10,transfer(),auth));noWrites();
 }
 @Test void unfinishedUnapprovedIncompleteAndCancelledOrdersCannotTransfer(){
  for(String flag:List.of("unfinished","pricePending","incomplete")){order.put(flag,1L);assertThrows(PreviewException.class,()->service.adjust(10,transfer(),auth));order.put(flag,0L);}
  for(int status:List.of(5,10)){order.put("status",status);assertThrows(PreviewException.class,()->service.adjust(10,transfer(),auth));}
  noWrites();
 }
 @Test void cancellationDoesNotPreventExplicitCorrectionOfMistakenPayment(){order.put("status",5);assertNotNull(service.adjust(10,reverse(),auth));}
 @Test void managerCannotAdjustEvenOwnPayment(){user.getRoles().iterator().next().setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));verifyNoInteractions(db);}
 @Test void differentCompanyContextCannotBeUsed(){doReturn(Map.of("mode","RESELLER","partnerId",21L)).when(service).context(100L,10L,1L,auth);assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));verifyNoInteractions(db);}
 @Test void sourceFromAnotherCustomerOrPartnerIsNotFound(){
  when(db.queryForList(startsWith("SELECT id,branch_no,order_no,operation"),eq(7L),eq(10L),eq(20L))).thenReturn(List.of());
  assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();
 }
 @Test void sourceBranchAccessIsRequired(){
  payment.put("branch_no",2L);doThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Недоступна філія")).when(access).branch(user,2L);
  assertThrows(PreviewException.class,()->service.adjust(10,reverse(),auth));noWrites();
 }
 @Test void reasonIsMandatory(){
  var r=reverse();var blank=new PartnerPaymentService.Adjustment(r.requestKey(),r.branchNo(),r.partnerId(),r.contextOrder(),r.operation(),r.sourcePaymentId(),r.orderNo(),r.amount(),r.expectedTotal(),r.expectedPaid()," ");
  assertThrows(PreviewException.class,()->service.adjust(10,blank,auth));noWrites();
 }
 @Test void amountScaleDoesNotBreakRetry(){
  var r=transfer();var scaled=new PartnerPaymentService.Adjustment(r.requestKey(),r.branchNo(),r.partnerId(),r.contextOrder(),r.operation(),r.sourcePaymentId(),r.orderNo(),n("100.00"),n("900.00"),n("1000.00"),r.note());
  assertTrue(PartnerPaymentService.sameAdjustment(r,scaled));
 }
 @Test void transferCannotExceedActualOverpayment(){
  var r=transfer();var tooMuch=new PartnerPaymentService.Adjustment(r.requestKey(),r.branchNo(),r.partnerId(),r.contextOrder(),r.operation(),r.sourcePaymentId(),r.orderNo(),n("100.01"),r.expectedTotal(),r.expectedPaid(),r.note());
  assertThrows(PreviewException.class,()->service.adjust(10,tooMuch,auth));noWrites();
 }
}
