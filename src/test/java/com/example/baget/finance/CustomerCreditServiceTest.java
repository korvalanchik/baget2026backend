package com.example.baget.finance;
import com.example.baget.billing.BillingAccess;
import com.example.baget.invoices.*;
import com.example.baget.users.User;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CustomerCreditServiceTest {
 JdbcTemplate db;CustomerPaymentAccess access;BillingAccess billing;CustomerCreditService service;InvoiceFinanceService finance;
 Authentication auth=new UsernamePasswordAuthenticationToken("accountant","",List.of());ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 String key="a70b4dd2-2b21-4089-97ea-0b7093c2a310";
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);access=mock(CustomerPaymentAccess.class);billing=mock(BillingAccess.class);finance=mock(InvoiceFinanceService.class);
  service=spy(new CustomerCreditService(db,access,billing,mock(InvoiceRepository.class),finance,json));
  when(billing.user(auth)).thenReturn(new User());doReturn(Map.of()).when(service).read(10,1,auth);
 }
 CustomerCreditService.Policy request(){return new CustomerCreditService.Policy(key,0,true,"Домовленість");}
 @Test void managerCannotGrantCredit(){doThrow(new IllegalStateException("denied")).when(billing).user(auth);assertThrows(IllegalStateException.class,()->service.savePolicy(10,1,request(),auth));verifyNoInteractions(db);}
 @Test void stalePolicyWritesNothing(){when(db.queryForList(startsWith("SELECT allow_unpaid"),eq(10L),eq(1L))).thenReturn(List.of(Map.of("allowed",false,"revision",1L)));assertThrows(PreviewException.class,()->service.savePolicy(10,1,request(),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void policyChangeHasAuditAndNoFinancialPosting(){service.savePolicy(10,1,request(),auth);verify(db).update(startsWith("INSERT INTO customer_credit_policy"),eq(10L),eq(1L),eq(true));verify(db).update(startsWith("INSERT INTO customer_credit_events"),eq(key),eq(10L),eq(1L),isNull(),eq("POLICY"),eq("accountant"),anyString());verifyNoInteractions(finance);}
 @Test void duplicateDoesNotChangePolicyAgain() throws Exception {
  var old=new HashMap<String,Object>();old.put("customer_id",10L);old.put("branch_no",1L);old.put("invoice_id",null);old.put("action","POLICY");old.put("actor","accountant");old.put("payload",json.writeValueAsString(request()));
  when(db.queryForList(startsWith("SELECT customer_id,branch_no,invoice_id"),eq(key))).thenReturn(List.of(old));
  service.savePolicy(10,1,request(),auth);verify(db,never()).update(anyString(),any(Object[].class));
 }
}
