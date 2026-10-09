package com.example.baget.cash;
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
class ReceiptPaymentsServiceTest {
 JdbcTemplate db;CashService cash;ReceiptPaymentsService service;Authentication auth;User admin;
 BigDecimal d(String s){return new BigDecimal(s);}
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);cash=mock(CashService.class);service=new ReceiptPaymentsService(db,cash,new ObjectMapper());auth=mock(Authentication.class);admin=new User();admin.setId(7L);admin.setUsername("accountant");var role=new Role();role.setName("ROLE_ADMIN");admin.setRoles(List.of(role));admin.setAllowedBranches(Set.of());when(cash.user(auth)).thenReturn(admin);when(cash.account(2L)).thenReturn(Map.of("id",2L,"branch_no",1L,"kind","BANK"));when(db.queryForList(startsWith("SELECT r.id,r.document_no"),eq(5L))).thenReturn(List.of(Map.of("id",5L,"documentNo","A-1","vendorName","Supplier","snapshot","{\"purchaseTotal\":10000.00,\"total\":10600.00,\"document\":{\"deliveryCost\":500.00,\"procurementCost\":50.00},\"lines\":[{\"directCost\":50.00}]}")));when(db.queryForObject("SELECT LAST_INSERT_ID()",Long.class)).thenReturn(20L);}
 ReceiptPaymentsService.Pay pay(String component,String amount,long revision){return new ReceiptPaymentsService.Pay("e2df0db6-ee69-4fc7-8da4-7a1600082525",2,component,d(amount),revision,"Supplier","payment-1","Paid");}
 Map<String,Object> payment(long id,String kind,String amount,Long reversed){var p=new HashMap<String,Object>();p.putAll(Map.of("id",id,"accountId",2L,"component","MATERIALS","kind",kind,"amount",d(amount),"direction",kind.equals("PAYMENT")?"OUT":"IN","payee","Supplier","documentRef","payment-1"));p.put("reversalOf",reversed);return p;}
 @Test void partialPaymentPostsOneCashEntryAndNoStockWrites(){service.pay(5,pay("MATERIALS","1000",0),auth);verify(cash).enough(2,d("1000"));verify(db).update(startsWith("INSERT INTO ledger_entries"),eq(1L),eq("OUT"),eq("MATERIAL_PURCHASE"),eq(d("1000")),eq(7L),eq(2L),eq(20L),eq("RECEIPT-5"),anyString());verify(db,never()).update(startsWith("UPDATE parts"),any(Object[].class));verify(db,never()).update(startsWith("UPDATE priced_material_receipts"),any(Object[].class));}
 @Test void deliveryHasItsOwnLimit(){assertThrows(PreviewException.class,()->service.pay(5,pay("DELIVERY","501",0),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void newKeyWithStaleRevisionCannotDuplicatePayment(){when(db.queryForList(startsWith("SELECT p.id,p.cash_account_id"),eq(5L))).thenReturn(List.of(payment(19,"PAYMENT","1000",null)));assertThrows(PreviewException.class,()->service.pay(5,pay("MATERIALS","1000",0),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void insufficientCashPostsNothing(){doThrow(CashService.bad("Недостатньо коштів")).when(cash).enough(2,d("1000"));assertThrows(PreviewException.class,()->service.pay(5,pay("MATERIALS","1000",0),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void exactRetryHasNoWrites(){when(cash.prior(eq("RECEIPT_PAYMENT"),anyString(),any(),eq(admin))).thenReturn(Map.of("id",20L));service.pay(5,pay("MATERIALS","1000",0),auth);verify(db,never()).update(anyString(),any(Object[].class));verify(cash,never()).enough(anyLong(),any());}
 @Test void managerCannotPay(){admin.getRoles().iterator().next().setName("ROLE_MANAGER");assertThrows(PreviewException.class,()->service.pay(5,pay("MATERIALS","1000",0),auth));verifyNoInteractions(db);}
 @Test void reversalCreditsOriginalAccountWithoutChangingReceipt(){when(db.queryForList(startsWith("SELECT p.id,p.cash_account_id"),eq(5L))).thenReturn(List.of(payment(19,"PAYMENT","1000",null)));service.reverse(5,new ReceiptPaymentsService.Reverse("key",19,19,"Помилковий запис"),auth);verify(db).update(startsWith("INSERT INTO ledger_entries"),eq(1L),eq("IN"),eq("MATERIAL_PURCHASE"),eq(d("1000")),eq(7L),eq(2L),eq(20L),eq("RECEIPT-5"),anyString());verify(db,never()).update(startsWith("UPDATE parts"),any(Object[].class));}
 @Test void cannotReverseTwice(){when(db.queryForList(startsWith("SELECT p.id,p.cash_account_id"),eq(5L))).thenReturn(List.of(payment(19,"PAYMENT","1000",null),payment(20,"REVERSAL","1000",19L)));assertThrows(PreviewException.class,()->service.reverse(5,new ReceiptPaymentsService.Reverse("key",19,20,"Помилковий запис"),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void paidValuesAreReadFromLedger(){var rows=List.of(payment(19,"PAYMENT","1000",null),payment(20,"REVERSAL","1000",19L));var due=ReceiptPaymentMath.dues(Map.of("MATERIALS",d("10000")),rows).get(0);assertEquals(0,due.paid().signum());assertEquals(0,due.remaining().compareTo(d("10000")));}
}
