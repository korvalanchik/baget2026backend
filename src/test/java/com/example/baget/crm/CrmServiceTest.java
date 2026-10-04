package com.example.baget.crm;

import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CrmServiceTest {
 JdbcTemplate db; UsersRepository users; CrmService service; Authentication auth;
 final String key="a70b4dd2-2b21-4089-97ea-0b7093c2a310";
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);service=spy(new CrmService(db,users,json));
  User u=new User();Role role=new Role();role.setName("ROLE_MANAGER");u.setRoles(List.of(role));
  Branch b=new Branch();b.setBranchNo(1L);u.setAllowedBranches(Set.of(b));
  when(users.findByUsername("manager")).thenReturn(Optional.of(u));
  auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
  when(db.queryForObject(startsWith("SELECT COUNT(*) FROM ("),eq(Long.class),eq(10L),eq(1L))).thenReturn(1L);
  when(db.queryForList(startsWith("SELECT is_partner"),eq(10L),eq(1L))).thenReturn(List.of(Map.of("partner",false,"revision",0L)));
  doReturn(Map.of("ok",true)).when(service).detail(10,1,0,auth);
 }
 CrmService.Contact note(){return new CrmService.Contact(key,0,"NOTE","Домовилися",null,null,LocalDate.of(2026,10,2));}
 @Test void branchAccessCheckedBeforeQueries(){
  assertThrows(PreviewException.class,()->service.contact(10,2,note(),auth));verifyNoInteractions(db);
 }
 @Test void missingCustomerRelationRejected(){
  when(db.queryForObject(startsWith("SELECT COUNT(*) FROM ("),eq(Long.class),eq(10L),eq(1L))).thenReturn(0L);
  assertThrows(PreviewException.class,()->service.contact(10,1,note(),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void staleRevisionPostsNothing(){
  when(db.queryForList(startsWith("SELECT is_partner"),eq(10L),eq(1L))).thenReturn(List.of(Map.of("partner",false,"revision",1L)));
  assertThrows(PreviewException.class,()->service.contact(10,1,note(),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void callbackNeedsDate(){
  var r=new CrmService.Contact(key,0,"CALL_BACK","Передзвонити",null,null,null);
  assertThrows(PreviewException.class,()->service.contact(10,1,r,auth));verifyNoInteractions(db);
 }
 @Test void contactWritesTenEventValues(){
  service.contact(10,1,note(),auth);
  verify(db).update(startsWith("INSERT INTO crm_contacts"),eq(key),anyString(),eq(10L),eq(1L),eq("NOTE"),eq("Домовилися"),isNull(),isNull(),eq(LocalDate.of(2026,10,2)),eq("manager"));
  verify(db).update("UPDATE crm_customer_profiles SET next_contact_date=?,revision=revision+1 WHERE customer_id=? AND branch_no=?",LocalDate.of(2026,10,2),10L,1L);
  verify(db,never()).update(contains("ledger_entries"),any(Object[].class));
 }
 @Test void duplicateRequestReturnsWithoutWrites() throws Exception {
  var r=note();
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json",json.writeValueAsString(r))));
  service.contact(10,1,r,auth);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void reusedKeyWithChangedPayloadRejected() throws Exception {
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json","{}")));
  assertThrows(PreviewException.class,()->service.contact(10,1,note(),auth));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void orderFromAnotherCustomerRejected(){
  when(db.queryForObject(startsWith("SELECT COUNT(*) FROM orders"),eq(Long.class),eq(900L),eq(10L),eq(1L))).thenReturn(0L);
  assertThrows(PreviewException.class,()->service.contact(10,1,new CrmService.Contact(key,0,"NOTE","Текст",900L,null,null),auth));
  verify(db,never()).update(startsWith("INSERT INTO crm_contacts"),any(Object[].class));
 }
 @Test void duplicateWithDocumentIdsAndRevisionReturnsWithoutWrites() throws Exception {
  var r=new CrmService.Contact(key,7,"NOTE","Текст",900L,42L,LocalDate.of(2026,10,2));
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json",json.writeValueAsString(r))));
  service.contact(10,1,r,auth);
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void duplicatePartnerRequestReturnsWithoutWrites() throws Exception {
  var r=new CrmService.Partner(key,3,true);
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json",json.writeValueAsString(r))));
  service.partner(10,1,r,auth);
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void sameKeyWithDifferentRevisionRemainsConflict() throws Exception {
  var old=note();
  var changed=new CrmService.Contact(key,1,old.outcome(),old.note(),old.orderNo(),old.invoiceId(),old.nextContactDate());
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json",json.writeValueAsString(old))));
  assertThrows(PreviewException.class,()->service.contact(10,1,changed,auth));
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void emptySearchAfterGlobalStringTrimmerIsAccepted() {
  when(db.query(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
    org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String,Object>>>any()))
    .thenReturn(List.of());
  for(String q:Arrays.asList(null,"","   ")) {
   var result=(Map<?,?>)service.list(null,"WORK",q,false,false,0,auth);
   assertEquals(List.of(),result.get("rows"));
   assertEquals(false,result.get("hasMore"));
  }
 }
 @Test void emptyTabDefaultsToWorkAndUnknownTabIsRejected() {
  when(db.query(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
    org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String,Object>>>any()))
    .thenReturn(List.of());
  assertDoesNotThrow(()->service.list(null,null,null,false,false,0,auth));
  assertThrows(PreviewException.class,()->service.list(null,"UNKNOWN",null,false,false,0,auth));
 }

 @Test void foremanCannotListFinancialGroup(){
  var u=users.findByUsername("manager").orElseThrow();var role=new Role();role.setName("ROLE_FOREMAN");u.setRoles(List.of(role));
  var e=assertThrows(PreviewException.class,()->service.list(1L,"SETTLEMENT","",false,false,0,auth));
  assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,e.getStatus());verifyNoInteractions(db);
 }
 @Test void settlementsCannotQueryAnotherBranch(){
  var e=assertThrows(PreviewException.class,()->service.list(2L,"SETTLEMENT","",false,false,0,auth));
  assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,e.getStatus());verifyNoInteractions(db);
 }
 @Test void retiredPartnerEndpointCannotWrite(){
  var controller=new CrmController(service);
  var e=assertThrows(PreviewException.class,()->controller.partner(10,1,new CrmService.Partner(key,0,true),auth));
  assertEquals(org.springframework.http.HttpStatus.GONE,e.getStatus());verifyNoInteractions(db);
 }

}
