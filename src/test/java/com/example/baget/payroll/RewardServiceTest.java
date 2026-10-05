package com.example.baget.payroll;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RewardServiceTest {
 JdbcTemplate db;UsersRepository users;RewardService service;Authentication auth;User admin;Map<String,Object> product,settings;
 BigDecimal d(String x){return new BigDecimal(x);}
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);service=new RewardService(db,users,new ObjectMapper());auth=mock(Authentication.class);when(auth.isAuthenticated()).thenReturn(true);when(auth.getName()).thenReturn("cashier");admin=new User();admin.setId(1L);Role role=new Role();role.setName("ROLE_ADMIN");admin.setRoles(List.of(role));admin.setAllowedBranches(Set.of());when(users.findByUsername("cashier")).thenReturn(Optional.of(admin));settings=new HashMap<>(Map.of("enabled",true,"rate",d("5"),"revision",0L));when(db.queryForMap(startsWith("SELECT enabled,rate,revision"))).thenReturn(settings);
 product=new HashMap<>(Map.of("orderNo",100L,"branchNo",2L,"authorId",7L,"mode","OWN","location","CUSTOMER","confirmed",1));when(db.queryForMap(startsWith("SELECT p.order_no"),eq(14L))).thenReturn(product);when(db.queryForObject(startsWith("SELECT COUNT(*) FROM reward_issues"),eq(Long.class),eq(14L))).thenReturn(0L);when(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,7L)).thenReturn(1L);when(db.queryForList(startsWith("SELECT ip.billed_amount"),eq(14L))).thenReturn(List.of(Map.of("amount",d("1000"))));when(db.queryForObject(startsWith("SELECT id FROM reward_accounts"),eq(Long.class),eq(7L),eq(2L))).thenReturn(9L);
 when(db.queryForList(startsWith("SELECT id,user_id"),eq(9L))).thenAnswer(i->List.of(new HashMap<>(Map.of("id",9L,"userId",7L,"branchNo",2L,"revision",3L))));when(db.queryForObject(startsWith("SELECT COALESCE(SUM(amount)"),eq(BigDecimal.class),eq(9L))).thenReturn(d("50"));}
 RewardService.Operation payout(String amount){return new RewardService.Operation("96f16f28-9ee6-4611-bef1-f2cbcd7db07c",9,3,"PAYOUT",d(amount),"CASH",null,"Виплачено");}
 void noWrites(){verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void defaultRateCreditsOrderAuthorNotIssuer(){service.accrue(14,"other-manager");verify(db).queryForObject(startsWith("SELECT id FROM reward_accounts"),eq(Long.class),eq(7L),eq(2L));verify(db).update(startsWith("INSERT INTO reward_entries"),eq(9L),eq(14L),eq(d("50.00")),anyString(),eq("other-manager"));}
 @Test void personalRateOverridesDefault(){when(db.queryForList("SELECT rate FROM reward_rates WHERE user_id=?",7L)).thenReturn(List.of(Map.of("rate",d("7.5"))));service.accrue(14,"cashier");verify(db).update(startsWith("INSERT INTO reward_entries"),eq(9L),eq(14L),eq(d("75.00")),anyString(),eq("cashier"));}
 @Test void disabledDoesNotAccrue(){settings.put("enabled",false);service.accrue(14,"cashier");noWrites();}
 @Test void resellerCannotAccrue(){product.put("mode","RESELLER");service.accrue(14,"cashier");noWrites();}
 @Test void pendingHandoverCannotAccrue(){product.put("location","BRANCH");assertThrows(PreviewException.class,()->service.accrue(14,"cashier"));noWrites();}
 @Test void repeatedProductDoesNotAccrueAgain(){when(db.queryForObject(startsWith("SELECT COUNT(*) FROM reward_issues"),eq(Long.class),eq(14L))).thenReturn(1L);service.accrue(14,"cashier");noWrites();}
 @Test void missingInvoiceGoesToReviewWithoutInventingAmount(){when(db.queryForList(startsWith("SELECT ip.billed_amount"),eq(14L))).thenReturn(List.of());service.accrue(14,"cashier");verify(db).update(startsWith("INSERT INTO reward_issues"),eq(14L),eq(100L),eq(2L),eq(7L),isNull(),eq(d("5")),eq(false),eq("REVIEW"),anyString(),eq("cashier"));verify(db,never()).update(startsWith("INSERT INTO reward_entries"),any(Object[].class));}
 @Test void excessivePayoutRejected(){assertThrows(PreviewException.class,()->service.post(payout("51"),auth));noWrites();}
 @Test void partialPayoutPostsNegativeAmount(){service.post(payout("20"),auth);verify(db).update(startsWith("INSERT INTO reward_entries"),eq(9L),eq("PAYOUT"),isNull(),isNull(),eq(d("-20")),eq("CASH"),eq("Виплачено"),eq("cashier"));}
 @Test void staleWalletRejected(){var p=payout("20");assertThrows(PreviewException.class,()->service.post(new RewardService.Operation(p.requestKey(),9,2,p.kind(),p.amount(),p.method(),null,p.note()),auth));noWrites();}
 @Test void managerCannotPayThemself(){Role r=new Role();r.setName("ROLE_MANAGER");admin.setRoles(List.of(r));assertThrows(PreviewException.class,()->service.post(payout("20"),auth));noWrites();}
 @Test void managerCannotReadOtherManager(){Role r=new Role();r.setName("ROLE_MANAGER");admin.setRoles(List.of(r));assertThrows(PreviewException.class,()->service.history(9,0,auth));}
 @Test void accountantCannotUseForeignBranch(){Role r=new Role();r.setName("ROLE_ACCOUNTANT");admin.setRoles(List.of(r));assertThrows(PreviewException.class,()->service.post(payout("20"),auth));noWrites();}
 @Test void exactRetryDoesNotPayTwice()throws Exception{var p=payout("20");when(db.queryForList(startsWith("SELECT actor,operation,payload,result"),eq(p.requestKey()))).thenReturn(List.of(Map.of("actor","cashier","operation","ENTRY","payload",new ObjectMapper().writeValueAsString(p),"result","{\"saved\":true}")));service.post(p,auth);noWrites();}
 @Test void roundingUsesKopecks(){assertEquals(d("7.51"),RewardMath.calculate(d("100.10"),d("7.5")));assertEquals(d("0.00"),RewardMath.calculate(d("100"),d("0")));assertThrows(IllegalArgumentException.class,()->RewardMath.calculate(d("100"),d("101")));}
}
