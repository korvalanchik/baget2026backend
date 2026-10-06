package com.example.baget.openingcost;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OpeningCostServiceTest {
 JdbcTemplate db;OpeningCostService service;Role role;Map<String,Object> part;
 final ObjectMapper json=new ObjectMapper();final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 OpeningCostService.Request request(){return new OpeningCostService.Request("5747efbc-d130-45e3-9024-4ea08fdbd104",2,new BigDecimal("10"),"METER",new BigDecimal("100"),"Накладна 1");}
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var user=new User();role=new Role();role.setName("ROLE_ADMIN");user.setRoles(List.of(role));when(users.findByUsername("admin")).thenReturn(Optional.of(user));service=new OpeningCostService(db,users,json);part=new HashMap<>();part.put("partNo",5L);part.put("description","Багет");part.put("version",2L);part.put("stock",new BigDecimal("10"));part.put("unit","METER");part.put("unitCost",null);when(db.queryForList(startsWith("SELECT PartNo AS partNo"),eq(5L))).thenReturn(List.of(part));}
 @Test void unauthorizedCannotRead(){role.setName("ROLE_USER");assertThrows(PreviewException.class,()->service.read(5,auth));verifyNoInteractions(db);}
 @Test void changedStockCannotWrite(){part.put("stock",new BigDecimal("9"));assertThrows(PreviewException.class,()->service.save(5,request(),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void knownCostCannotBeOverwritten(){part.put("unitCost",BigDecimal.ZERO);assertThrows(PreviewException.class,()->service.save(5,request(),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void nonPositiveStockCannotWrite(){part.put("stock",BigDecimal.ZERO);assertThrows(PreviewException.class,()->service.save(5,request(),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void saveChangesOnlyCostAndAddsHistory(){service.save(5,request(),auth);verify(db).update("UPDATE parts SET inventory_unit_cost=?,inventory_cost_unit=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",new BigDecimal("100"),"METER",5L);verify(db).update(eq("INSERT INTO opening_material_cost_events(request_key,part_no,actor,request_json,snapshot) VALUES(?,?,?,?,?)"),eq(request().requestKey()),eq(5L),eq("admin"),anyString(),anyString());}
 @Test void replayWorksAfterCostWasSet() throws Exception {part.put("unitCost",new BigDecimal("100"));when(db.queryForList("SELECT part_no,actor,request_json,snapshot FROM opening_material_cost_events WHERE request_key=?",request().requestKey())).thenReturn(List.of(Map.of("part_no",5L,"actor","admin","request_json",json.writeValueAsString(request()),"snapshot","{\"unitCost\":100}")));assertEquals(json.readTree("{\"unitCost\":100}"),service.save(5,request(),auth));verify(db,never()).update(anyString(),any(Object[].class));}
}
