package com.example.baget.reports;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OwnerResultsServiceTest {
 JdbcTemplate db;OwnerResultsService service;Role role;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var u=new User();role=new Role();role.setName("ROLE_ADMIN");u.setRoles(List.of(role));when(users.findByUsername("admin")).thenReturn(Optional.of(u));service=new OwnerResultsService(db,users);}
 @Test void deniesManagerBeforeQueries(){role.setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.report(LocalDate.now(),LocalDate.now(),null,0,auth));verifyNoInteractions(db);}
 @Test void reversedPeriodRejected(){assertThrows(PreviewException.class,()->service.report(LocalDate.of(2026,10,7),LocalDate.of(2026,10,6),null,0,auth));verifyNoInteractions(db);}
 @Test void periodUsesKyivAndSummaryIncludesOtherPages(){var from=LocalDate.of(2026,10,1);var to=LocalDate.of(2026,10,7);var begin=Timestamp.from(from.atStartOfDay(ZoneId.of("Europe/Kyiv")).toInstant());var end=Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Europe/Kyiv")).toInstant());var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<51;i++)rows.add(OwnerResultsMathTest.row("100","20","5",0,"POSTED"));when(db.queryForList(OwnerResultsService.SQL,begin,end,2L,2L)).thenReturn(rows);var report=(Map<?,?>)service.report(from,to,2L,1,auth);assertEquals(1,((List<?>)report.get("rows")).size());assertEquals(51,((Map<?,?>)report.get("summary")).get("productCount"));assertEquals(false,report.get("hasMore"));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void exportIncludesAllRows(){var day=LocalDate.of(2026,10,1);var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<51;i++)rows.add(OwnerResultsMathTest.row("100","20","5",0,"POSTED"));when(db.queryForList(eq(OwnerResultsService.SQL),any(Object[].class))).thenReturn(rows);var report=(Map<?,?>)service.report(day,day,null,-1,auth);assertEquals(51,((List<?>)report.get("rows")).size());assertEquals(false,report.get("hasMore"));}
}
