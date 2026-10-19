package com.example.baget.cash;
import com.example.baget.users.*;
import com.example.baget.branch.Branch;
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
class CashServiceTest {
 JdbcTemplate db;CashService service;User user;Authentication auth;Map<String,Object> from,central,transit,transfer;
 BigDecimal d(String x){return new BigDecimal(x);}
 Map<String,Object> account(long id,String kind){return new HashMap<>(Map.of("id",id,"kind",kind,"branch_no",1L,"initialized",true));}
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);service=new CashService(db,users,new ObjectMapper());
  user=new User();user.setId(7L);user.setUsername("operator");role("ROLE_ADMIN");var b=new Branch();b.setBranchNo(1L);user.setAllowedBranches(Set.of(b));
  auth=mock(Authentication.class);when(auth.isAuthenticated()).thenReturn(true);when(auth.getName()).thenReturn("operator");when(users.findByUsername("operator")).thenReturn(Optional.of(user));
  from=account(1,"BRANCH_CASH");central=account(2,"CENTRAL_CASH");transit=account(3,"TRANSIT");
  for(var a:List.of(from,central,transit))when(db.queryForList("SELECT * FROM cash_accounts WHERE id=? FOR UPDATE",a.get("id"))).thenReturn(List.of(a));
  when(db.queryForObject(startsWith("SELECT COUNT(*) FROM branch_commercial_settings"),eq(Long.class),eq(1L))).thenReturn(1L);
  when(db.queryForObject(startsWith("SELECT COALESCE(SUM(CASE"),eq(BigDecimal.class),anyLong())).thenReturn(d("500"));
  when(db.queryForList("SELECT id FROM cash_accounts WHERE code='transit'")).thenReturn(List.of(Map.of("id",3L)));
  when(db.queryForList("SELECT id FROM cash_accounts WHERE code='central'")).thenReturn(List.of(Map.of("id",2L)));
  when(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)).thenReturn(40L);
  transfer=new HashMap<>(Map.of("id",40L,"source_id",1L,"destination_id",2L,"transit_id",3L,"amount",d("100"),"state","SENT","sent_by","manager"));
  when(db.queryForList("SELECT * FROM cash_transfers WHERE id=? FOR UPDATE",40L)).thenReturn(List.of(transfer));
 }
 void role(String name){var role=new Role();role.setName(name);user.setRoles(List.of(role));}
 String key(){return "17ff2359-2a85-4099-bce2-7eae6b876cdf";}
 CashService.Send send(){return new CashService.Send(key(),1,2,d("100"),"До офісу");}
 void noLedger(){verify(db,never()).update(startsWith("INSERT INTO ledger_entries"),any(Object[].class));}
 void leg(long account,String direction,String category,Long transferId,Long reward){verify(db).update(startsWith("INSERT INTO ledger_entries"),eq(1L),eq(direction),eq(category),eq(d("100")),eq(7L),eq(account),eq(transferId),eq(reward),anyString(),anyString());}
 @Test void sendMovesSourceToTransitOnly(){role("ROLE_MANAGER");service.send(send(),auth);leg(1,"OUT","CASH_TRANSFER",40L,null);leg(3,"IN","CASH_TRANSFER",40L,null);verify(db,times(2)).update(startsWith("INSERT INTO ledger_entries"),any(Object[].class));}
 @Test void receiveMovesTransitToCentral(){service.receive(new CashService.Receive(key(),40,d("100"),"Перераховано"),auth);leg(3,"OUT","CASH_TRANSFER",40L,null);leg(2,"IN","CASH_TRANSFER",40L,null);}
 @Test void differenceIncludingZeroPostsNoMoney(){service.receive(new CashService.Receive(key(),40,BigDecimal.ZERO,"Не отримано"),auth);noLedger();verify(db).update(startsWith("INSERT INTO cash_transfer_notes"),eq(40L),eq(BigDecimal.ZERO),eq("Не отримано"),eq("operator"));}
 @Test void senderCannotConfirmOwnTransfer(){transfer.put("sent_by","operator");assertThrows(PreviewException.class,()->service.receive(new CashService.Receive(key(),40,d("100"),"Отримано"),auth));noLedger();}
 @Test void managerCannotReceiveCentralFunds(){role("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.receive(new CashService.Receive(key(),40,d("100"),"Отримано"),auth));noLedger();}
 @Test void shortagePreventsBothLegs(){when(db.queryForObject(startsWith("SELECT COALESCE(SUM(CASE"),eq(BigDecimal.class),eq(1L))).thenReturn(d("99"));assertThrows(PreviewException.class,()->service.send(send(),auth));noLedger();}
 @Test void exactRetryPostsNothing()throws Exception {var r=send();when(db.queryForList(startsWith("SELECT actor,operation,payload,result"),eq(key()))).thenReturn(List.of(Map.of("actor","operator","operation","SEND","payload",new ObjectMapper().writeValueAsString(r),"result","{\"id\":40,\"state\":\"SENT\"}")));service.send(r,auth);noLedger();verify(db,never()).update(startsWith("INSERT INTO cash_transfers"),any(Object[].class));}
 @Test void initializedAndOwnBranchRequiredForPayment(){from.put("initialized",false);assertThrows(PreviewException.class,()->service.paymentAccount(user,1,1L));from.put("initialized",true);when(db.queryForObject(startsWith("SELECT COUNT(*) FROM branch_commercial_settings"),eq(Long.class),eq(1L))).thenReturn(0L);assertThrows(PreviewException.class,()->service.paymentAccount(user,1,1L));}
 @Test void paymentCannotSelectForeignBranchOrTransit(){role("ROLE_MANAGER");from.put("branch_no",9L);assertThrows(PreviewException.class,()->service.paymentAccount(user,1,1L));assertThrows(PreviewException.class,()->service.paymentAccount(user,1,3L));assertThrows(PreviewException.class,()->service.paymentAccount(user,1,2L));}
 @SuppressWarnings("unchecked")
 @Test void overviewCanBeScopedToActiveBranch(){
  role("ROLE_MANAGER");var other=new Branch();other.setBranchNo(1L);var studio=new Branch();studio.setBranchNo(8L);user.setAllowedBranches(Set.of(other,studio));
  List<Map<String,Object>> accountRows=List.of(Map.of("id",11L,"kind","BRANCH_CASH","branch_no",1L),Map.of("id",88L,"kind","BRANCH_CASH","branch_no",8L));
  when(db.queryForList(startsWith("SELECT a.*,b.BranchName AS branchName"),any(Object[].class))).thenReturn(accountRows);
  when(db.queryForList(startsWith("SELECT t.*,"),any(Object[].class))).thenReturn(List.of());
  when(db.queryForList("SELECT id,name,kind FROM cash_accounts WHERE kind='CENTRAL_CASH' AND initialized=1",any(Object[].class))).thenReturn(List.of());
  Map<String,Object> result=(Map<String,Object>)service.overview(auth,8L);List<Map<String,Object>> visible=(List<Map<String,Object>>)result.get("accounts");
  assertEquals(1,visible.size());assertEquals(88L,visible.get(0).get("id"));
 }
 @Test void rewardUsesCentralCashOnly(){service.reward(55,null,d("100"),user);leg(2,"OUT","MANAGER_REWARD",null,55L);}
 @Test void managerCannotPostReward(){role("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.reward(55,null,d("100"),user));noLedger();}
 @Test void historicalRewardReversalDoesNotInventCash(){service.reward(55,54L,d("100"),user);noLedger();}
 @Test void rewardReversalCreditsOriginalCentralCash(){when(db.queryForList(startsWith("SELECT cash_account_id FROM ledger_entries WHERE reward_entry_id"),eq(54L))).thenReturn(List.of(Map.of("cash_account_id",2L)));service.reward(55,54L,d("100"),user);leg(2,"IN","MANAGER_REWARD",null,55L);}
 @Test void cannotOpenBalanceTwice(){assertThrows(PreviewException.class,()->service.opening(new CashService.Opening(key(),1,d("100"),"Перераховано"),auth));noLedger();}
}
