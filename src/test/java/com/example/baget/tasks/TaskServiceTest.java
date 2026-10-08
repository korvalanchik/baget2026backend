package com.example.baget.tasks;
import com.example.baget.users.*;
import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskServiceTest {
 JdbcTemplate db;UsersRepository users;User user;TaskService service;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("manager","",List.of());
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);user=new User();user.setEnabled(true);user.setAllowedBranches(Set.of());roles("ROLE_MANAGER");when(users.findByUsername("manager")).thenReturn(Optional.of(user));service=new TaskService(db,users);}
 void roles(String... names){user.setRoles(Arrays.stream(names).map(n->{var r=new Role();r.setName(n);return r;}).toList());}
 @Test void managerCannotReadWorkshopQueue(){assertThrows(PreviewException.class,()->service.queue("work",0,auth));verifyNoInteractions(db);}
 @Test void anonymousAndDisabledUsersAreDeniedBeforeSql(){assertThrows(PreviewException.class,()->service.overview(null));user.setEnabled(false);assertThrows(PreviewException.class,()->service.overview(auth));verifyNoInteractions(db);}
 @Test void maliciousTaskAndNegativePageRejected(){assertThrows(PreviewException.class,()->service.queue("issue OR 1=1",0,auth));assertThrows(PreviewException.class,()->service.queue("issue",-1,auth));verifyNoInteractions(db);}
 @Test void emptyBranchSetCannotReadAllBranches(){service.queue("issue",0,auth);verify(db).queryForList(contains("AND 1=0"),eq(0));}
 @Test void assignedBranchIsBoundAndPaginationUsesLookahead(){var b=new Branch();b.setBranchNo(7L);user.setAllowedBranches(Set.of(b));var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<21;i++)rows.add(Map.of("orderNo",i));when(db.queryForList(anyString(),eq(7L),eq(20))).thenReturn(rows);var result=(Map<?,?>)service.queue("issue",1,auth);assertEquals(20,((List<?>)result.get("rows")).size());assertEquals(true,result.get("hasMore"));verify(db).queryForList(contains("o.BranchNo IN (?)"),eq(7L),eq(20));}
 @Test void rolesAreCombinedWithoutGrantingManagerRightsToForeman(){roles("ROLE_FOREMAN");assertEquals(List.of("workshop"),TaskService.groups(user));roles("ROLE_MANAGER","ROLE_ACCOUNTANT");assertEquals(List.of("manager","accountant"),TaskService.groups(user));}
 @Test void adminContactsRetainCrmAssignedScope(){roles("ROLE_ADMIN");service.queue("contacts",0,auth);verify(db).queryForList(contains("AND 1=0"),any(java.time.LocalDate.class),eq(0));}
}
