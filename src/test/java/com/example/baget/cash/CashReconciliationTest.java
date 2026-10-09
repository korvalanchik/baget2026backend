package com.example.baget.cash;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CashReconciliationTest {
 JdbcTemplate db;CashService service;Authentication auth;ObjectMapper json;Map<String,Object> account;
 BigDecimal d(String x){return new BigDecimal(x);}
 Map<String,Object> row(String period,String direction,String category,String amount,long id){return Map.of("period",period,"direction",direction,"category",category,"amount",d(amount),"lastId",id);}
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);json=new ObjectMapper().findAndRegisterModules();service=new CashService(db,users,json);var user=new User();user.setUsername("admin");user.setId(1L);var role=new Role();role.setName("ROLE_ADMIN");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());auth=mock(Authentication.class);when(auth.isAuthenticated()).thenReturn(true);when(auth.getName()).thenReturn("admin");when(users.findByUsername("admin")).thenReturn(Optional.of(user));account=new HashMap<>(Map.of("id",1L,"branch_no",1L,"kind","BRANCH_CASH","initialized",true));when(db.queryForList("SELECT * FROM cash_accounts WHERE id=? FOR UPDATE",1L)).thenReturn(List.of(account));when(db.queryForObject(startsWith("SELECT COUNT(*) FROM branch_commercial_settings"),eq(Long.class),eq(1L))).thenReturn(1L);when(db.queryForList(startsWith("SELECT CASE WHEN created_at"),any(java.sql.Timestamp.class),eq(1L),any(java.sql.Timestamp.class))).thenReturn(List.of(row("BEFORE","IN","CASH_OPENING","1000",1),row("DAY","IN","CUSTOMER_ADVANCE","300",2),row("DAY","OUT","CASH_TRANSFER","800",3)));when(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)).thenReturn(7L);}
 CashService.Reconcile request(String actual,String note){return new CashService.Reconcile("90a04e46-abd9-443d-95a1-91c6680101d1",1,LocalDate.now(CashService.CASH_ZONE),3,d("500"),d(actual),note);}
 @Test void snapshotSeparatesOpeningAndDailyTurnover(){var r=CashDayMath.summarize(List.of(row("BEFORE","IN","CASH_OPENING","1000",1),row("DAY","IN","CUSTOMER_ADVANCE","300",2),row("DAY","OUT","CASH_TRANSFER","800",3)));assertEquals(0,r.expected().compareTo(d("500")));assertEquals(0,r.incoming().compareTo(d("300")));assertEquals(0,r.outgoing().compareTo(d("800")));assertEquals(3,r.lastLedgerId());}
 @Test void observationNeverPostsMoney(){service.reconcile(request("490","Не вистачає 10 грн"),auth);verify(db).update(startsWith("INSERT INTO cash_reconciliations"),eq(1L),any(LocalDate.class),eq(3L),eq(d("500")),eq(d("490")),eq(d("-10")),eq("admin"),anyString(),anyString());verify(db,never()).update(contains("ledger_entries"),any(Object[].class));}
 @Test void differenceNeedsExplanation(){assertThrows(PreviewException.class,()->service.reconcile(request("490",""),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void equalCashAllowsEmptyNote(){service.reconcile(request("500",""),auth);verify(db).update(startsWith("INSERT INTO cash_reconciliations"),any(Object[].class));}
 @Test void unchangedBalanceButNewMovementRequiresRefresh(){var s=CashDayMath.summarize(List.of(row("DAY","IN","CUSTOMER_ADVANCE","500",4)));assertThrows(IllegalArgumentException.class,()->CashDayMath.verify(s,3,d("500"),d("500"),""));}
 @Test void cannotSavePastDay(){var r=request("500","");assertThrows(PreviewException.class,()->service.reconcile(new CashService.Reconcile(r.requestKey(),1,r.day().minusDays(1),3,d("500"),d("500"),""),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void bankIsNotCash(){account.put("kind","BANK");assertThrows(PreviewException.class,()->service.reconcile(request("500",""),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void retryReturnsWithoutSecondObservation()throws Exception{var r=request("500","");when(db.queryForList(startsWith("SELECT actor,operation,payload,result"),eq(r.requestKey()))).thenReturn(List.of(Map.of("actor","admin","operation","RECONCILE","payload",json.writeValueAsString(r),"result","{\"id\":7}")));service.reconcile(r,auth);verify(db,never()).update(anyString(),any(Object[].class));}
}
