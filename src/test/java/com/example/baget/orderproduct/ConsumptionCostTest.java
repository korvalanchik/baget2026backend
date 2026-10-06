package com.example.baget.orderproduct;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsumptionCostTest {
 JdbcTemplate db;ConsumptionCostController controller;Role role;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var user=new User();role=new Role();role.setName("ROLE_ADMIN");user.setRoles(List.of(role));when(users.findByUsername("admin")).thenReturn(Optional.of(user));controller=new ConsumptionCostController(db,users);when(db.queryForList("SELECT id FROM order_products WHERE id=?",5L)).thenReturn(List.of(Map.of("id",5L)));}
 Map<String,Object> row(String cost,String quantity){var r=new HashMap<String,Object>();r.put("unitCost",cost==null?null:new BigDecimal(cost));r.put("quantity",new BigDecimal(quantity));return r;}
 @Test void nonAdminCannotRead(){role.setName("ROLE_FOREMAN");assertThrows(PreviewException.class,()->controller.read(5,auth));verifyNoInteractions(db);}
 @Test void usesMovementSnapshotIncludingRework(){when(db.queryForList(startsWith("SELECT i.OrderNo"),eq(5L))).thenReturn(List.of(row("100","2"),row("110","1")));var result=(Map<?,?>)controller.read(5,auth);assertEquals(new BigDecimal("310.00"),result.get("total"));assertEquals(0,result.get("unknownCount"));}
 @Test void unknownIsNotZero(){when(db.queryForList(startsWith("SELECT i.OrderNo"),eq(5L))).thenReturn(List.of(row("100","2"),row(null,"1")));var result=(Map<?,?>)controller.read(5,auth);assertNull(result.get("total"));assertEquals(new BigDecimal("200.00"),result.get("knownTotal"));assertEquals(1,result.get("unknownCount"));}
 @Test void knownZeroIsValid(){when(db.queryForList(startsWith("SELECT i.OrderNo"),eq(5L))).thenReturn(List.of(row("0","2")));var result=(Map<?,?>)controller.read(5,auth);assertEquals(new BigDecimal("0.00"),result.get("total"));assertEquals(0,result.get("unknownCount"));}
 @Test void noMovementsIsNotAnEvaluatedProduct(){var result=(Map<?,?>)controller.read(5,auth);assertNull(result.get("total"));}
 @Test void captureDoesNotWriteLegacyCostOrQuantity(){ConsumptionCost.capture(db,1,2);verify(db).update(startsWith("UPDATE inventory_movements m JOIN items"),eq(1L),eq(2L));verify(db).update(startsWith("UPDATE items i JOIN inventory_movements"),eq(1L),eq(2L));verifyNoMoreInteractions(db);}
}
