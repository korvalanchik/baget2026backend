package com.example.baget.crm;
import com.example.baget.users.*;
import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PickupServiceTest {
 JdbcTemplate db;CrmService service;Authentication auth;Map<String,Object> ready;
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 String key="a70b4dd2-2b21-4089-97ea-0b7093c2a310";
 @BeforeEach void setup() {
  db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);
  var u=new User();var role=new Role();role.setName("ROLE_MANAGER");u.setRoles(List.of(role));var b=new Branch();b.setBranchNo(1L);u.setAllowedBranches(Set.of(b));
  when(users.findByUsername("manager")).thenReturn(Optional.of(u));auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
  service=spy(new CrmService(db,users,json));doReturn(Map.of("ok",true)).when(service).detail(10,1,0,auth);
  when(db.queryForObject(startsWith("SELECT COUNT(*) FROM ("),eq(Long.class),eq(10L),eq(1L))).thenReturn(1L);
  when(db.queryForList(startsWith("SELECT OrderNo FROM orders"),eq(900L),eq(10L),eq(1L))).thenReturn(List.of(Map.of("OrderNo",900L)));
  ready=new HashMap<>(Map.of("orderNo",900L,"orderStatus",3,"productId",20L,"state","COMPLETED","location","BRANCH","completedAt","2026-09-29 12:00:00","productionRevision",2L,"trackingRevision",4L,"revision",0L));
  when(db.queryForList(contains("AS notifiedSnapshot"),eq(10L),eq(1L))).thenAnswer(i->List.of(new HashMap<>(ready)));
  when(db.queryForObject("SELECT id FROM crm_contacts WHERE request_key=?",Long.class,key)).thenReturn(100L);
 }
 CrmService.Pickup request(){return new CrmService.Pickup(key,0,(String)service.pickupOrders(10,1).get(0).get("readySignature"),"PICKUP_AGREED","Забере завтра",LocalDate.of(2026,9,30));}
 @Test void agreementDoesNotChangeNextCallOrStock() {
  service.pickup(10,1,900,request(),auth);
  verify(db).update(startsWith("UPDATE crm_order_pickup SET"),eq(LocalDate.of(2026,9,30)),eq(100L),eq(true),eq(100L),eq(900L));
  verify(db,never()).update(contains("crm_customer_profiles"),any(Object[].class));
  verify(db,never()).update(contains("UPDATE parts"),any(Object[].class));
 }
 @Test void staleReadinessPostsNothing() {
  var r=request();ready.put("trackingRevision",5L);
  assertThrows(PreviewException.class,()->service.pickup(10,1,900,r,auth));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void legacyAndPendingTransferNeverPromisePickup() {
  ready.put("pending","WORKSHOP");
  assertEquals(0,service.pickupOrders(10,1).get(0).get("branchReady"));
  ready.put("productId",null);
  var row=service.pickupOrders(10,1).get(0);assertEquals(true,row.get("legacy"));assertEquals(0,row.get("branchReady"));
 }
 @Test void retryDoesNotWriteAgain() throws Exception {
  var r=request();
  when(db.queryForList(startsWith("SELECT customer_id,branch_no"),eq(key))).thenReturn(List.of(Map.of("customer_id",10L,"branch_no",1L,"actor","manager","request_json",json.writeValueAsString(r))));
  when(db.queryForMap("SELECT order_no FROM crm_contacts WHERE request_key=?",key)).thenReturn(Map.of("order_no",900L));
  service.pickup(10,1,900,r,auth);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void otherBranchIsDeniedBeforeReadingOrder(){
  assertThrows(PreviewException.class,()->service.pickup(10,2,900,new CrmService.Pickup(key,0,"x","PICKUP_NOTIFIED","",null),auth));
  verifyNoInteractions(db);
 }
}
