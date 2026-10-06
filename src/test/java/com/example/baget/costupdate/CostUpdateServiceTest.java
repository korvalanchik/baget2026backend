package com.example.baget.costupdate;
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
class CostUpdateServiceTest {
 JdbcTemplate db;UsersRepository users;CostUpdateService service;Role role;
 final ObjectMapper json=new ObjectMapper();
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 final String key="5747efbc-d130-45e3-9024-4ea08fdbd104";
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);var u=new User();role=new Role();role.setName("ROLE_ADMIN");u.setRoles(List.of(role));when(users.findByUsername("admin")).thenReturn(Optional.of(u));service=new CostUpdateService(db,users,json);when(db.queryForMap(startsWith("SELECT rate,revision FROM cost_update_settings"))).thenAnswer(i->new HashMap<>(Map.of("rate",new BigDecimal("20"),"revision",0L)));}
 Map<String,Object> part(long id,String cost){var p=new LinkedHashMap<String,Object>();p.put("partNo",id);p.put("description","Багет");p.put("unit","METER");p.put("oldCost",new BigDecimal("90"));p.put("baseCost",cost==null?null:new BigDecimal(cost));p.put("personalRate",null);p.put("version",1L);return p;}
 @SuppressWarnings("unchecked") Map<String,Object> preview(){when(db.queryForList(contains("LIMIT 100 OFFSET"),eq(""),eq(""),eq(0))).thenReturn(List.of(part(5,"100")));return ((List<Map<String,Object>>)((Map<String,Object>)service.list(null,0,auth)).get("rows")).get(0);}
 @Test void previewOnlyAndUnknownCost(){var p=preview();assertEquals(new BigDecimal("120.00"),p.get("newCost"));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void rejectsAccessBeforeReading(){role.setName("ROLE_USER");assertThrows(PreviewException.class,()->service.list("",0,auth));verifyNoInteractions(db);}
 @Test void stalePriceCannotWrite(){var p=preview();when(db.queryForList(endsWith("p.PartNo=? FOR UPDATE"),eq(5L))).thenReturn(List.of(part(5,"101")));assertThrows(PreviewException.class,()->service.apply(new CostUpdateService.Apply(key,List.of(new CostUpdateService.Selection(5,p.get("token").toString()))),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void allRowsCheckedBeforeAnyWrite(){var p=preview();when(db.queryForList(endsWith("p.PartNo=? FOR UPDATE"),eq(5L))).thenReturn(List.of(part(5,"100")));when(db.queryForList(endsWith("p.PartNo=? FOR UPDATE"),eq(6L))).thenReturn(List.of(part(6,null)));assertThrows(PreviewException.class,()->service.apply(new CostUpdateService.Apply(key,List.of(new CostUpdateService.Selection(5,p.get("token").toString()),new CostUpdateService.Selection(6,"unknown"))),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void repeatedRequestReturnsSavedResultWithoutWrites() throws Exception {var request=new CostUpdateService.Apply(key,List.of(new CostUpdateService.Selection(5,"old")));when(db.queryForList("SELECT actor,request_json,snapshot FROM cost_update_events WHERE request_key=?",key)).thenReturn(List.of(Map.of("actor","admin","request_json",json.writeValueAsString(request),"snapshot","{\"rows\":[]}")));assertEquals(json.readTree("{\"rows\":[]}"),service.apply(request,auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void confirmedUpdateWritesOnlyCostAndHistory(){var p=preview();when(db.queryForList(endsWith("p.PartNo=? FOR UPDATE"),eq(5L))).thenReturn(List.of(part(5,"100")));when(db.queryForObject("SELECT CAST(Cost AS DECIMAL(24,2)) FROM parts WHERE PartNo=?",BigDecimal.class,5L)).thenReturn(new BigDecimal("120.00"));service.apply(new CostUpdateService.Apply(key,List.of(new CostUpdateService.Selection(5,p.get("token").toString()))),auth);verify(db).update("UPDATE parts SET Cost=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",new BigDecimal("120.00"),5L);verify(db).update(eq("INSERT INTO cost_update_events(request_key,action,actor,request_json,snapshot) VALUES(?,?,?,?,?)"),eq(key),eq("APPLY"),eq("admin"),anyString(),anyString());}
}
