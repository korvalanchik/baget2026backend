package com.example.baget.stocktake;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class StocktakeServiceTest {
 JdbcTemplate db;UsersRepository users;InventoryReservations reservations;StocktakeService service;Authentication auth;
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();Map<String,Object> doc,line;StocktakeService.Stamp current;
 String key="8b9c1740-f60d-497b-8a64-e1502dd4c89b";
 BigDecimal n(int x){return BigDecimal.valueOf(x);}
 @BeforeEach void setup() throws Exception {
  db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);reservations=mock(InventoryReservations.class);service=spy(new StocktakeService(db,users,reservations,json));
  User u=new User();Role role=new Role();role.setName("ROLE_WAREHOUSE");u.setRoles(List.of(role));when(users.findByUsername("store")).thenReturn(Optional.of(u));auth=new UsernamePasswordAuthenticationToken("store","",List.of());
  doc=new HashMap<>();doc.put("id",1L);doc.put("revision",0L);doc.put("status","DRAFT");
  current=new StocktakeService.Stamp(n(5),1L,"METER","MATERIAL",3,4);
  line=new HashMap<>();line.put("partNo",10L);line.put("description","Багет");line.put("unit","METER");line.put("baseline",json.writeValueAsString(current));line.put("counted",n(2));line.put("note","Нестача");
  when(db.queryForMap(startsWith("SELECT id,request_key"),eq(1L))).thenAnswer(i->new HashMap<>(doc));
  when(db.queryForList(startsWith("SELECT part_no AS partNo"),eq(1L))).thenAnswer(i->List.of(new HashMap<>(line)));
  when(db.query(startsWith("SELECT CAST(p.OnHand"),org.mockito.ArgumentMatchers.<RowMapper<StocktakeService.Stamp>>any(),eq(10L))).thenAnswer(i->List.of(current));
  when(db.query(startsWith("SELECT r.id,r.order_product_id"),org.mockito.ArgumentMatchers.<RowMapper<StocktakeRules.Reservation>>any(),eq(10L))).thenReturn(List.of(new StocktakeRules.Reservation(1,11,101,n(4),n(3)),new StocktakeRules.Reservation(2,22,202,n(3),n(2))));
  when(db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,10L)).thenReturn(n(2));
  doReturn(Map.of("id",1L)).when(service).read(1,auth);
 }
 StocktakeService.Review review(){return service.review(1,new StocktakeService.Revision(0),auth);}
 StocktakeService.Post request(String token,boolean approved){return new StocktakeService.Post(key,0,token,approved);}
 @Test void previewDoesNotWriteAndShowsAffectedOrders(){var r=review();assertTrue(r.canPost());assertTrue(r.requiresApproval());assertEquals(64,r.token().length());verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void movementEvenWithSameBalanceRequiresRecount(){current=new StocktakeService.Stamp(n(5),1L,"METER","MATERIAL",4,4);assertFalse(review().canPost());}
 @Test void nullCountAndMissingReasonBlockPosting(){line.put("counted",null);assertFalse(review().canPost());line.put("counted",n(2));line.put("note","");assertFalse(review().canPost());}
 @Test void shortageRequiresExplicitApprovalBeforeAnyWrite(){var r=review();assertThrows(PreviewException.class,()->service.post(1,request(r.token(),false),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void changedReservationsInvalidateReviewedToken(){var r=review();when(db.query(startsWith("SELECT r.id,r.order_product_id"),org.mockito.ArgumentMatchers.<RowMapper<StocktakeRules.Reservation>>any(),eq(10L))).thenReturn(List.of());assertThrows(PreviewException.class,()->service.post(1,request(r.token(),true),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void approvedCountChangesOnlyReservedAmountNotDemand(){var r=review();service.post(1,request(r.token(),true),auth);verify(db).update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",n(2),10L);verify(db).update("UPDATE material_reservations SET reserved=? WHERE id=?",n(2),1L);verify(db).update("UPDATE material_reservations SET reserved=? WHERE id=?",n(0),2L);verify(db,never()).update(startsWith("UPDATE orders"),any(Object[].class));verify(db,never()).update(contains("SET demand="),any(Object[].class));}
 @Test void retryAfterLostResponseDoesNotCountTwice() throws Exception {var r=request(review().token(),true);doc.put("status","POSTED");doc.put("revision",1L);doc.put("post_key",key);doc.put("postedBy","store");doc.put("post_request",json.writeValueAsString(r));service.post(1,r,auth);verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void differentPostKeyCannotRepost(){doc.put("status","POSTED");doc.put("post_key","another-key");assertThrows(PreviewException.class,()->service.post(1,request("a".repeat(64),true),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void staleDocumentCannotReview(){doc.put("revision",1L);assertThrows(PreviewException.class,()->review());verifyNoInteractions(reservations);}
 @Test void storedPrecisionMismatchFails(){when(db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,10L)).thenReturn(new BigDecimal("1.999999"));var r=review();assertThrows(PreviewException.class,()->service.post(1,request(r.token(),true),auth));verify(db,never()).update(startsWith("INSERT INTO material_stock_operations"),any(Object[].class));}
 @Test void databaseRoleIsAuthoritative(){when(users.findByUsername("store")).thenReturn(Optional.empty());assertThrows(PreviewException.class,()->review());verifyNoInteractions(db,reservations);}
 @Test void numericScaleAloneDoesNotInvalidateBaseline(){assertTrue(StocktakeService.sameStamp(current,new StocktakeService.Stamp(new BigDecimal("5.000000"),1L,"METER","MATERIAL",3,4)));}
 @Test void cancellationBindsAllFiveEventColumns() throws Exception {
  var request=new StocktakeService.Cancel(0,"Помилково створений акт");
  service.cancel(1,request,auth);
  verify(db).update(
   "INSERT INTO stocktake_events(document_id,revision,action,actor,snapshot) VALUES(?,?,?,?,?)",
   1L,1L,"CANCEL","store",json.writeValueAsString(request));
 }

}
