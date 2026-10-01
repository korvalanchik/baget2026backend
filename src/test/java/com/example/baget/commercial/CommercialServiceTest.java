package com.example.baget.commercial;
import com.example.baget.users.*;
import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CommercialServiceTest {
 JdbcTemplate db;UsersRepository users;CommercialService service;User user;Authentication auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
 BigDecimal n(String s){return new BigDecimal(s);}
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);service=new CommercialService(db,users,new PartPriceResolver(),new ObjectMapper());user=new User();var role=new Role();role.setName("ROLE_MANAGER");user.setRoles(List.of(role));var branch=new Branch();branch.setBranchNo(1L);user.setAllowedBranches(Set.of(branch));when(users.findByUsername("manager")).thenReturn(Optional.of(user));}
 @Test void unmeasuredProductCannotStart(){when(db.queryForList("SELECT state,calculation_hash FROM product_price_approval WHERE product_id=?",10L)).thenReturn(List.of(Map.of("state","UNMEASURED")));assertThrows(PreviewException.class,()->service.requireReady(10));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void managerCannotChangeBranchOwnership(){assertThrows(PreviewException.class,()->service.configure(1,new CommercialService.Settings(0,"OWN",null,"Test"),auth));verifyNoInteractions(db);}
 @Test void foremanCannotApproveCustomerPrice(){user.getRoles().iterator().next().setName("ROLE_FOREMAN");assertThrows(PreviewException.class,()->service.decide(10,new CommercialService.Decision(1,true,"Test"),auth));verifyNoInteractions(db);}
 @Test void legacyProductKeepsExistingWorkflow(){assertDoesNotThrow(()->service.requireReady(10));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void outsideToleranceCreatesHoldWithoutStockOrFinancialWrites(){
  when(db.queryForList("SELECT * FROM product_price_approval WHERE product_id=?",10L)).thenReturn(List.of(Map.of("baseline_retail",n("100"),"revision",0L,"state","UNMEASURED")));
  when(db.queryForMap(startsWith("SELECT measured_width_mm"),eq(10L))).thenReturn(Map.of("measured_width_mm",n("300"),"measured_height_mm",n("400")));
  when(db.queryForList(startsWith("SELECT ItemNo,PartNo,actual_consumption"),eq(10L))).thenReturn(List.of(Map.of("ItemNo",1L,"PartNo",2L,"actual_consumption",n("2"),"billing_unit_price",n("60"),"reseller_unit_price",n("40"))));
  service.measured(10,"master","Обмір");
  verify(db).update(startsWith("UPDATE product_price_approval SET current_retail"),eq(n("120.00")),eq(n("80.00")),eq("WAITING"),anyString(),eq(10L));
  verify(db).update("UPDATE order_products SET production_state='ACCEPTED' WHERE id=? AND production_state='IN_PROGRESS'",10L);
  verify(db,never()).update(contains("ledger_entries"),any(Object[].class));verify(db,never()).update(startsWith("UPDATE parts"),any(Object[].class));
 }
 @Test void approvalFromWrongBranchIsDeniedBeforeLock(){when(db.queryForList(startsWith("SELECT p.order_no AS orderNo"),eq(10L))).thenReturn(List.of(Map.of("orderNo",1L,"branchNo",2L)));assertThrows(PreviewException.class,()->service.decide(10,new CommercialService.Decision(1,true,"Test"),auth));verify(db,never()).queryForObject(contains("FOR UPDATE"),eq(Long.class),any());}
}
