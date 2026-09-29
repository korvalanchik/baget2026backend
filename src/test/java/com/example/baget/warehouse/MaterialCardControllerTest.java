package com.example.baget.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialCardControllerTest {
 @Test void authorizationFailureDoesNotReadHistory() {
  var db=mock(JdbcTemplate.class);var warehouse=mock(WarehouseService.class);
  when(warehouse.material(10,null)).thenThrow(new IllegalStateException("denied"));
  assertThrows(IllegalStateException.class,()->new MaterialCardController(db,warehouse).card(10,null,null,"ALL",0,null));
  verifyNoInteractions(db);
 }
 @Test void invalidPeriodDoesNotReadHistory() {
  var db=mock(JdbcTemplate.class);var warehouse=mock(WarehouseService.class);
  when(warehouse.material(10,null)).thenReturn(new HashMap<>());
  assertThrows(com.example.baget.calculation.preview.PreviewException.class,()->new MaterialCardController(db,warehouse).card(10,LocalDate.of(2026,9,30),LocalDate.of(2026,9,29),"ALL",0,null));
  verifyNoInteractions(db);
 }
 @Test void unknownStockIsPreservedAndPaginationUsesExtraRow() {
  var db=mock(JdbcTemplate.class);var warehouse=mock(WarehouseService.class);
  var detail=new HashMap<String,Object>();detail.put("partNo",10L);detail.put("onHand",null);detail.put("reservations",List.of());
  when(warehouse.material(10,null)).thenReturn(detail);
  when(db.queryForMap(anyString(),eq(10L))).thenReturn(Map.of("demand",new BigDecimal("6"),"reserved",new BigDecimal("4")));
  when(db.queryForList(contains("FROM supplier_order_lines"),eq(10L))).thenReturn(List.of(Map.of("quantity",new BigDecimal("3"))));
  var events=new ArrayList<Map<String,Object>>();for(int i=0;i<101;i++)events.add(Map.of("eventKey","I"+i));
  when(db.queryForList(startsWith("SELECT * FROM ("),any(Object[].class))).thenAnswer(invocation->{
   var args=invocation.getArguments();String sql=(String)args[0];
   assertEquals(sql.chars().filter(c->c=='?').count(),args.length-1);
   assertEquals(LocalDate.of(2026,9,1).atStartOfDay(),args[4]);
   assertEquals(LocalDate.of(2026,10,1).atStartOfDay(),args[5]);
   assertEquals("COUNT",args[6]);assertEquals(100,args[7]);
   return events;
  });
  var result=(Map<?,?>)new MaterialCardController(db,warehouse).card(10,LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),"COUNT",1,null);
  assertNull(result.get("onHand"));assertNull(result.get("available"));
  assertEquals(new BigDecimal("2"),result.get("shortage"));assertEquals(new BigDecimal("3"),result.get("expected"));
  assertEquals(true,result.get("hasMore"));assertEquals(100,((List<?>)result.get("events")).size());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
