package com.example.baget.orderhistory;
import com.example.baget.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderHistoryTest {
 static class DB extends JdbcTemplate {
  String sql,mode="OWN";Object[] args;boolean missing;int eventReads;
  @Override public List<Map<String,Object>> queryForList(String s,Object...a){sql=s;args=a;
   if(s.startsWith("SELECT * FROM (")){eventReads++;return List.of();}
   if(s.contains("FROM order_products WHERE"))return List.of(Map.of("id",11L));
   if(s.contains("WHERE o.OrderNo=?"))return missing?List.of():List.of(Map.of("branchNo",1L,"commerceMode",mode));
   return List.of();
  }
 }
 private OrderHistoryService service(DB db,String roleName,boolean enabled){
  var repo=mock(UsersRepository.class);var u=new User();var r=new Role();r.setName(roleName);u.setRoles(List.of(r));u.setEnabled(enabled);u.setAllowedBranches(Set.of());when(repo.findByUsername("user")).thenReturn(Optional.of(u));return new OrderHistoryService(db,repo);
 }
 private final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("user","",List.of());
 @Test void nullSearchIsEmptyAndNeverQueriesOrders(){var db=new DB();service(db,"ROLE_MANAGER",true).search(null,0,auth);assertNull(db.sql);}
 @Test void noBranchesCannotSearchAllOrders(){var db=new DB();service(db,"ROLE_MANAGER",true).search("Іван",0,auth);assertTrue(db.sql.contains("AND 1=0"));}
 @Test void disabledUserCannotRead(){var db=new DB();assertThrows(ResponseStatusException.class,()->service(db,"ROLE_ADMIN",false).read(42,"ALL",null,0,auth));assertNull(db.sql);}
 @Test void inaccessibleOrderDoesNotReadAnyEvents(){var db=new DB();db.missing=true;assertThrows(ResponseStatusException.class,()->service(db,"ROLE_MANAGER",true).read(42,"ALL",null,0,auth));assertEquals(0,db.eventReads);}
 @Test void foremanHasNoFinanceOrContactsInUnion(){var db=new DB();service(db,"ROLE_FOREMAN",true).read(42,"ALL",null,0,auth);assertFalse(db.sql.contains("ledger_entries"));assertFalse(db.sql.contains("crm_contacts"));}
 @Test void resellerManagerCannotRequestEnterpriseFinance(){var db=new DB();db.mode="RESELLER";assertThrows(ResponseStatusException.class,()->service(db,"ROLE_MANAGER",true).read(42,"FINANCE",null,0,auth));assertEquals(0,db.eventReads);}
 @Test void unknownProductRejectedAndKnownProductIsBound(){var db=new DB();var s=service(db,"ROLE_ADMIN",true);assertThrows(ResponseStatusException.class,()->s.read(42,"ALL",999L,0,auth));s.read(42,"MOVEMENT",11L,0,auth);assertTrue(db.sql.contains("AND productId=?"));assertEquals(11L,db.args[db.args.length-2]);}
 @Test void phoneSearchStripsFormattingAndKeepsParameters(){var db=new DB();service(db,"ROLE_ADMIN",true).search("(050) 123-45",0,auth);assertEquals("05012345",db.args[2]);assertFalse(db.sql.contains("05012345"));}
}
