package com.example.baget.reports;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PartnerResultsControllerTest {
 JdbcTemplate db;PartnerResultsController controller;Role role;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 final LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,7);
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var u=new User();role=new Role();role.setName("ROLE_ADMIN");u.setRoles(List.of(role));when(users.findByUsername("admin")).thenReturn(Optional.of(u));controller=new PartnerResultsController(db,users);}
 @Test void nonAdminCannotRead(){role.setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->controller.report(from,to,null,null,0,auth));verifyNoInteractions(db);}
 @Test void invalidPeriodCannotQuery(){assertThrows(PreviewException.class,()->controller.report(to,from,null,null,0,auth));verifyNoInteractions(db);}
 @Test void debtIsNotFilteredBySalesPeriodOrBranch(){var start=Timestamp.from(from.atStartOfDay(ZoneId.of("Europe/Kyiv")).toInstant());var end=Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Europe/Kyiv")).toInstant());var row=OwnerResultsMathTest.row("1000","300","0",0,null);row.put("invoiceId",9L);when(db.queryForList(PartnerResultsController.SALES,start,end,8L,8L,2L,2L)).thenReturn(List.of(row));when(db.queryForList(PartnerResultsController.DEBTS,8L,8L)).thenReturn(List.of(Map.of("partnerId",8L,"debt",new BigDecimal("500"))));var result=(Map<?,?>)controller.report(from,to,8L,2L,0,auth);assertEquals(new BigDecimal("500"),result.get("debtTotal"));var summary=(Map<?,?>)result.get("summary");assertEquals(1L,summary.get("invoiceCount"));assertEquals(new BigDecimal("700.00"),summary.get("difference"));verify(db,never()).update(anyString(),any(Object[].class));}
}
