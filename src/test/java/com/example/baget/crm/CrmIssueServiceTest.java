package com.example.baget.crm;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.finance.ProductPaymentCheck;
import com.example.baget.users.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
class CrmIssueServiceTest {
 JdbcTemplate db;CrmService crm;CustomerPaymentAccess access;ProductPaymentCheck payment;CrmIssueService service;Authentication auth;User user;Map<String,Object> product;Map<String,Object> header;ObjectMapper json=new ObjectMapper();
 final String key="96f16f28-9ee6-4611-bef1-f2cbcd7db07c";
 CrmIssueService.Issue request(){return new CrmIssueService.Issue(key,List.of(new CrmIssueService.Selection(1,3)),false,"Отримано");}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);crm=mock(CrmService.class);access=mock(CustomerPaymentAccess.class);payment=mock(ProductPaymentCheck.class);auth=mock(Authentication.class);user=new User();
  when(access.user(auth)).thenReturn(user);when(auth.getName()).thenReturn("manager");
  header=new HashMap<>(Map.of("orderNo",100L,"status",11));
  when(db.queryForList(startsWith("SELECT OrderNo AS orderNo"),eq(100L),eq(10L),eq(2L))).thenAnswer(i->List.of(new HashMap<>(header)));
  product=new HashMap<>(Map.of("id",1L,"state","COMPLETED","confirmed",1,"location","BRANCH","revision",3L));
  when(db.queryForList(startsWith("SELECT id,client_product_id"),eq(100L))).thenAnswer(i->List.of(new HashMap<>(product)));
  when(payment.read(1L)).thenReturn(Map.of("customerId",10L,"invoiceId",50L,"canIssue",true));when(payment.lock(1L)).thenReturn(50L);when(payment.checkIssue(1L,50L)).thenReturn(Map.of());
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);
  service=new CrmIssueService(db,crm,access,payment,json);
 }
 void noWrites(){verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void issuesAndRecordsAuditAndCompletesWholeOrder(){service.issue(10,2,100,request(),auth);verify(payment).checkIssue(1L,50L);verify(db).update(startsWith("UPDATE order_products SET current_location='CUSTOMER'"),eq(1L),eq(3L));verify(db).update(startsWith("UPDATE orders SET StatusOrder=12"),eq(100L));verify(db).update(startsWith("INSERT INTO crm_contacts"),eq(key),anyString(),eq(10L),eq(2L),anyString(),eq(100L),eq("manager"));}
 @Test void partialIssueDoesNotCompleteOrder(){var sibling=new HashMap<>(product);sibling.put("id",2L);sibling.put("location","WORKSHOP");when(db.queryForList(startsWith("SELECT id,client_product_id"),eq(100L))).thenReturn(List.of(new HashMap<>(product),sibling));service.issue(10,2,100,request(),auth);verify(db,never()).update(startsWith("UPDATE orders SET StatusOrder=12"),anyLong());}
 @Test void staleRevisionCannotIssue(){product.put("revision",4L);assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void pendingTransferCannotIssue(){product.put("pending","CUSTOMER");assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void warehouseProductCannotIssue(){product.put("location","WAREHOUSE");assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void reworkCannotIssue(){product.put("reworkId",9L);assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void cancelledOrderCannotIssue(){header.put("status",5);assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void debtIsCheckedOnServerBeforeWrites(){when(payment.checkIssue(1L,50L)).thenThrow(new PreviewException(org.springframework.http.HttpStatus.CONFLICT,"DEBT","Борг"));assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void resellerNeedsExplicitCheckAcknowledgement(){when(payment.read(1L)).thenReturn(Map.of("customerId",10L,"externalSettlement",true,"canIssue",true));assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void exactRetryDoesNotIssueTwice()throws Exception {when(db.queryForList(startsWith("SELECT customer_id,branch_no,order_no,outcome"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",2L,"order_no",100L,"outcome","PICKUP_ISSUED","actor","manager","request_json",json.writeValueAsString(request()))));var result=(Map<?,?>)service.issue(10,2,100,request(),auth);assertEquals(true,result.get("duplicate"));noWrites();verifyNoInteractions(payment);}
 @Test void sameKeyForAnotherOrderIsRejected()throws Exception {when(db.queryForList(startsWith("SELECT customer_id,branch_no,order_no,outcome"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",2L,"order_no",101L,"outcome","PICKUP_ISSUED","actor","manager","request_json",json.writeValueAsString(request()))));assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));noWrites();}
 @Test void branchAccessIsCheckedBeforeLoadingProducts(){doThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Філія" )).when(access).branch(user,2L);assertThrows(PreviewException.class,()->service.issue(10,2,100,request(),auth));verifyNoInteractions(db,payment);}
 @Test void invalidSecondProductPreventsAllWrites(){var r=new CrmIssueService.Issue(key,List.of(new CrmIssueService.Selection(1,3),new CrmIssueService.Selection(2,1)),false,"");assertThrows(PreviewException.class,()->service.issue(10,2,100,r,auth));noWrites();}
 void qrRow(){when(db.queryForList(startsWith("SELECT p.id AS productId"),eq(key))).thenReturn(List.of(Map.of("productId",1L,"orderNo",100L,"customerId",10L,"branchNo",2L)));}
 @Test void qrReturnsContactOnlyAfterBranchAuthorization(){
  qrRow();when(db.queryForList(startsWith("SELECT c.Company"),eq(10L),eq(2L))).thenReturn(List.of(Map.of("company","Клієнт","mobile","0123","branchName","Філія")));
  var result=service.resolveQr(key.toUpperCase(Locale.ROOT),auth);assertEquals(1L,result.get("productId"));assertEquals("0123",result.get("mobile"));
  var sequence=inOrder(access,db,crm);sequence.verify(access).user(auth);sequence.verify(db).queryForList(startsWith("SELECT p.id AS productId"),eq(key));sequence.verify(access).branch(user,2L);sequence.verify(crm).customer(10L,2L,auth);sequence.verify(db).queryForList(startsWith("SELECT c.Company"),eq(10L),eq(2L));noWrites();verifyNoInteractions(payment);
 }
 @Test void qrOfForeignBranchCannotReadContacts(){qrRow();doThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Філія")).when(access).branch(user,2L);assertThrows(PreviewException.class,()->service.resolveQr(key,auth));verify(db,never()).queryForList(startsWith("SELECT c.Company"),anyLong(),anyLong());verifyNoInteractions(crm,payment);noWrites();}
 @Test void qrRequiresPaymentRoleBeforeLookup(){when(access.user(auth)).thenThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Роль"));assertThrows(PreviewException.class,()->service.resolveQr(key,auth));verifyNoInteractions(db,crm,payment);}
 @Test void qrCannotSkipCrmCustomerScope(){qrRow();doThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Клієнт")).when(crm).customer(10L,2L,auth);assertThrows(PreviewException.class,()->service.resolveQr(key,auth));verify(db,never()).queryForList(startsWith("SELECT c.Company"),anyLong(),anyLong());noWrites();}
 @Test void invalidQrCannotQueryDatabase(){for(String token:new String[]{"", "1-1-1-1-1", "https://example.com/?product="+key, "not-a-token"})assertThrows(PreviewException.class,()->service.resolveQr(token,auth));verifyNoInteractions(db,crm,payment);}
 @Test void unknownQrIsNotFoundWithoutContactsOrWrites(){var ex=assertThrows(PreviewException.class,()->service.resolveQr(key,auth));assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,ex.getStatus());verify(db,never()).queryForList(startsWith("SELECT c.Company"),anyLong(),anyLong());noWrites();}

}
