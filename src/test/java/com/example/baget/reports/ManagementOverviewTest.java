package com.example.baget.reports;
import com.example.baget.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ManagementOverviewTest {
 private final LocalDate today=LocalDate.of(2026,10,11);
 @Test void futureAndReversedPeriodsRejected(){assertThrows(ResponseStatusException.class,()->ManagementOverviewService.validate(today,today.plusDays(1),null,"ALL",today));assertThrows(ResponseStatusException.class,()->ManagementOverviewService.validate(today,today.minusDays(1),null,"ALL",today));}
 @Test void inclusiveYearBoundaryAndNullMode(){assertDoesNotThrow(()->ManagementOverviewService.validate(today.minusDays(365),today,null,"ALL",today));assertThrows(ResponseStatusException.class,()->ManagementOverviewService.validate(today.minusDays(366),today,null,"ALL",today));assertThrows(ResponseStatusException.class,()->ManagementOverviewService.validate(today,today,null,null,today));}
 @Test void disabledAdminAndAccountantCannotRead(){
  var db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var u=new User();var role=new Role();role.setName("ROLE_ADMIN");u.setRoles(List.of(role));u.setEnabled(false);when(users.findByUsername("user")).thenReturn(Optional.of(u));var service=new ManagementOverviewService(db,users,mock(OwnerResultsService.class));var a=new UsernamePasswordAuthenticationToken("user","",List.of());
  assertThrows(ResponseStatusException.class,()->service.read(today,today,null,"ALL",a));u.setEnabled(true);role.setName("ROLE_ACCOUNTANT");assertThrows(ResponseStatusException.class,()->service.read(today,today,null,"ALL",a));verifyNoInteractions(db);
 }
 static class DB extends JdbcTemplate {
  List<String> queries=new ArrayList<>();
  @Override public List<Map<String,Object>> queryForList(String s,Object...a){queries.add(s);return List.of();}
  @Override public Map<String,Object> queryForMap(String s,Object...a){queries.add(s);return Map.of("unfinished",0);}
  @Override public <T>T queryForObject(String s,Class<T> type,Object...a){queries.add(s);return type.cast(0L);}
 }
 @Test void resellerFilterNeverCallsOwnFinancialReport(){
  var db=new DB();var users=mock(UsersRepository.class);var u=new User();var role=new Role();role.setName("ROLE_ADMIN");u.setRoles(List.of(role));u.setEnabled(true);when(users.findByUsername("user")).thenReturn(Optional.of(u));var owner=mock(OwnerResultsService.class);var service=new ManagementOverviewService(db,users,owner);
  var r=service.read(today,today,1L,"RESELLER",new UsernamePasswordAuthenticationToken("user","",List.of()));assertNull(r.get("ownResults"));verifyNoInteractions(owner);assertTrue(db.queries.stream().allMatch(s->s.contains("o.BranchNo=?")&&s.contains("o.commerce_mode=?")));
 }
 @Test void invalidBranchRejected(){assertThrows(ResponseStatusException.class,()->ManagementOverviewService.validate(today,today,0L,"OWN",today));}
}
