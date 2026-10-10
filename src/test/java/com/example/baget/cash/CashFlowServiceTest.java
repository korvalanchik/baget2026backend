package com.example.baget.cash;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
class CashFlowServiceTest {
 JdbcTemplate db;CashService cash;CashFlowService service;Authentication auth;User user;
 LocalDate day=LocalDate.now(CashService.CASH_ZONE);
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);cash=mock(CashService.class);service=new CashFlowService(db,cash);auth=mock(Authentication.class);user=new User();user.setUsername("accountant");var role=new Role();role.setName("ROLE_ACCOUNTANT");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());when(cash.user(auth)).thenReturn(user);when(db.queryForObject("SELECT COUNT(*) FROM cash_accounts",Long.class)).thenReturn(2L);}
 @Test void managerCannotRead(){user.getRoles().iterator().next().setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.report(day,day,auth));verifyNoInteractions(db);}
 @Test void invalidDatesNeverQuery(){assertThrows(PreviewException.class,()->service.report(day,day.plusDays(1),auth));assertThrows(PreviewException.class,()->service.report(day,day.minusDays(1),auth));assertThrows(PreviewException.class,()->service.report(day.minusDays(366),day,auth));verifyNoInteractions(db);}
 @Test void hiddenBranchesMakeReportPartial(){var r=(Map<?,?>)service.report(day,day,auth);assertEquals(false,r.get("completeScope"));verify(db).queryForList(contains("AND a.kind<>'BRANCH_CASH' ORDER BY a.id"),any(Object[].class));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void adminWithAllAccountsHasCompleteReport(){user.getRoles().iterator().next().setName("ROLE_ADMIN");when(db.queryForList(startsWith("SELECT a.id,a.name"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L,"initialized",true),Map.of("id",2L,"initialized",false)));var r=(Map<?,?>)service.report(day,day,auth);assertEquals(true,r.get("completeScope"));assertEquals(1L,r.get("uninitializedCount"));assertEquals(2,((List<?>)r.get("rows")).size());}
}
