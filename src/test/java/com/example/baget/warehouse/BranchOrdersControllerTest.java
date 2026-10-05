package com.example.baget.warehouse;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import com.example.baget.branch.Branch;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BranchOrdersControllerTest {
 JdbcTemplate db;User user;BranchOrdersController controller;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);user=new User();var role=new Role();role.setName("ROLE_MANAGER");user.setRoles(List.of(role));var branch=new Branch();branch.setBranchNo(2L);user.setAllowedBranches(Set.of(branch));when(users.findByUsername("manager")).thenReturn(Optional.of(user));controller=new BranchOrdersController(db,new CustomerPaymentAccess(users,db));
 }
 @Test void otherBranchDoesNotExposeOrderDetails(){
  when(db.queryForList("SELECT BranchNo FROM orders WHERE OrderNo=?",Long.class,42L)).thenReturn(List.of(3L));
  assertThrows(PreviewException.class,()->controller.read(42L,auth));
  verify(db).queryForList("SELECT BranchNo FROM orders WHERE OrderNo=?",Long.class,42L);verifyNoMoreInteractions(db);
 }
 @Test void noBranchesMeansEmptyQueueWithoutQuery(){user.setAllowedBranches(Set.of());assertEquals(Map.of("rows",List.of(),"hasMore",false),controller.list("RECEIVE",0,auth));verifyNoInteractions(db);}
 @Test void invalidQueueAndPageAreRejected(){assertThrows(PreviewException.class,()->controller.list("OTHER",0,auth));assertThrows(PreviewException.class,()->controller.list("READY",-1,auth));verifyNoInteractions(db);}
}
