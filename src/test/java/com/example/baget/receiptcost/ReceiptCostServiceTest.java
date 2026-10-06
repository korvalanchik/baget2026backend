package com.example.baget.receiptcost;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ReceiptCostServiceTest {
 JdbcTemplate db;ReceiptCostService service;InventoryReservations reservations;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("warehouse","",List.of());
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);reservations=mock(InventoryReservations.class);var u=new User();var role=new Role();role.setName("ROLE_WAREHOUSE");u.setRoles(List.of(role));when(users.findByUsername("warehouse")).thenReturn(Optional.of(u));service=new ReceiptCostService(db,users,reservations,new ObjectMapper().findAndRegisterModules());when(db.queryForList("SELECT VendorNo FROM vendors WHERE VendorNo=?",Long.class,1L)).thenReturn(List.of(1L));}
 ReceiptCostService.Request request(BigDecimal opening){return new ReceiptCostService.Request("5747efbc-d130-45e3-9024-4ea08fdbd104",1,"N1",LocalDate.of(2026,10,6),null,null,new BigDecimal("200"),BigDecimal.ZERO,"",List.of(new ReceiptCostService.Line(5,new BigDecimal("20"),new BigDecimal("100"),BigDecimal.ZERO,opening)));}
 void part(boolean known){var row=new HashMap<String,Object>();row.put("Description","Багет");row.put("unit_type","METER");row.put("stock",new BigDecimal("10"));row.put("inventory_cost_unit","METER");row.put("inventory_unit_cost",known?new BigDecimal("90"):null);when(db.queryForMap(startsWith("SELECT Description,unit_type"),eq(5L))).thenReturn(row);}
 @Test void previewDoesNotWriteAndUsesKnownAverage(){part(true);var p=service.preview(request(null),auth);assertEquals(new BigDecimal("103.333333"),p.lines().get(0).averageAfter());assertFalse(p.lines().get(0).openingCostUsed());verify(db,never()).update(anyString(),any(Object[].class));verifyNoInteractions(reservations);}
 @Test void missingOpeningCostBlocksPreview(){part(false);assertThrows(PreviewException.class,()->service.preview(request(null),auth));var p=service.preview(request(new BigDecimal("90")),auth);assertTrue(p.lines().get(0).openingCostUsed());}
 @Test void staleReviewCannotPost(){part(true);assertThrows(PreviewException.class,()->service.post(new ReceiptCostService.Post(request(null),"stale"),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void duplicateKeyReturnsExistingDocumentWithoutStockWrites(){
  var mapper=new ObjectMapper().findAndRegisterModules();var req=new ReceiptCostService.Post(request(null),"known");
  try{when(db.queryForList("SELECT id,request_json,actor FROM priced_material_receipts WHERE request_key=?",req.document().requestKey())).thenReturn(List.of(Map.of("id",9L,"request_json",mapper.writeValueAsString(req),"actor","warehouse")));}catch(Exception e){throw new IllegalStateException(e);}
  var stored=new HashMap<String,Object>();stored.put("id",9L);stored.put("snapshot","{}");when(db.queryForMap("SELECT id,snapshot,actor,created_at AS createdAt FROM priced_material_receipts WHERE id=?",9L)).thenReturn(stored);
  assertNotNull(service.post(req,auth));verify(db,never()).update(anyString(),any(Object[].class));verifyNoInteractions(reservations);
 }
}
