package com.example.baget.billing;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.customer.CustomerTransactionRepository;
import com.example.baget.invoices.Invoice;
import com.example.baget.invoices.InvoiceRepository;
import com.example.baget.reconciliation.PartnerReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PartnerReceivablesServiceTest {
 JdbcTemplate db;PartnerReconciliationService reconciliation;CustomerTransactionRepository transactions;InvoiceRepository invoices;PartnerReceivablesService service;Authentication auth;
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);reconciliation=mock(PartnerReconciliationService.class);transactions=mock(CustomerTransactionRepository.class);invoices=mock(InvoiceRepository.class);auth=mock(Authentication.class);
  when(reconciliation.options(auth)).thenReturn(List.of(Map.of("id",20L,"name","Фірма")));
  when(transactions.calculateAvailableAdvance(20L)).thenReturn(new BigDecimal("40"));
  service=new PartnerReceivablesService(db,reconciliation,transactions,invoices);
 }
 @Test void deniedCompanyCannotReadBalances(){assertThrows(PreviewException.class,()->service.read(21L,0,auth));verifyNoInteractions(db,transactions,invoices);}
 @Test void noAccessibleCompaniesReturnsEmptyRegistry(){when(reconciliation.options(auth)).thenReturn(List.of());var result=service.read(null,0,auth);assertTrue(((List<?>)result.get("rows")).isEmpty());verifyNoInteractions(db,transactions,invoices);}
 @Test void selectedCompanyWithNoDebtStillShowsAdvance(){var result=service.read(20L,0,auth);var group=(Map<?,?>)((List<?>)result.get("groups")).get(0);assertEquals(BigDecimal.ZERO,group.get("debt"));assertEquals(new BigDecimal("40"),group.get("availableAdvance"));}
 @Test void totalsAreNotReducedByAdvance(){
  when(db.queryForList(startsWith("SELECT co.partner_id AS partnerId"),any(Object[].class))).thenReturn(List.of(new HashMap<>(Map.of("partnerId",20L,"partnerName","Фірма","invoiceCount",3L,"debt",new BigDecimal("300")))));
  var group=(Map<?,?>)((List<?>)service.read(null,0,auth).get("groups")).get(0);assertEquals(new BigDecimal("300"),group.get("debt"));assertEquals(new BigDecimal("40"),group.get("availableAdvance"));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void paginationUsesExtraRowAndAgeUsesKyivDate(){
  var rows=new ArrayList<Map<String,Object>>();var models=new ArrayList<Invoice>();
  for(long id=1;id<=51;id++){rows.add(new HashMap<>(Map.of("id",id,"debt",BigDecimal.TEN)));var i=new Invoice();i.setId(id);i.setCreatedAt(LocalDate.now(InvoiceAge.ZONE).atStartOfDay(InvoiceAge.ZONE).toOffsetDateTime());models.add(i);}
  when(db.queryForList(startsWith("SELECT i.id,i.invoice_no"),any(Object[].class))).thenReturn(rows);when(invoices.findAllById(any())).thenReturn(models);
  var result=service.read(null,0,auth);assertEquals(true,result.get("hasNext"));var shown=(List<?>)result.get("rows");assertEquals(50,shown.size());assertEquals(0L,((Map<?,?>)shown.get(0)).get("ageDays"));
 }
 @Test void missingInvoiceDateFailsInsteadOfInventingAge(){when(db.queryForList(startsWith("SELECT i.id,i.invoice_no"),any(Object[].class))).thenReturn(List.of(new HashMap<>(Map.of("id",1L))));assertThrows(PreviewException.class,()->service.read(null,0,auth));}
 @Test void roleFailureIsPropagatedBeforeDataQueries(){when(reconciliation.options(auth)).thenThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"DENIED","Немає прав"));assertThrows(PreviewException.class,()->service.read(null,0,auth));verifyNoInteractions(db,transactions,invoices);}
 @Test void filterIsAppliedInSqlBeforePagination(){
  service.read(20L,1,auth);
  verify(db).queryForList(argThat(sql->sql.startsWith("SELECT i.id,i.invoice_no") && sql.contains("co.partner_id IN (?)") && sql.endsWith("LIMIT 51 OFFSET ?")),eq(20L),eq(50));
 }
 @Test void selectAndFromHaveSeparatorInBothExecutedQueries(){
  service.read(20L,0,auth);
  verify(db).queryForList(argThat(sql->sql.startsWith("SELECT co.partner_id") && sql.contains(" AS debt FROM invoices i")),eq(20L));
  verify(db).queryForList(argThat(sql->sql.startsWith("SELECT i.id") && sql.contains("l.debt FROM invoices i")),eq(20L),eq(0));
 }
}
