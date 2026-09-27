package com.example.baget.billing;
import com.example.baget.finance.PaymentOrchestrator;
import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.ledger.*;
import com.example.baget.users.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class InvoicePostingTest {
 @Test void mergeMovesDebtWithoutDuplicatingIt(){
  var ledger=mock(LedgerService.class);var mapper=mock(CustomerTransactionMapper.class);var finance=mock(InvoiceFinanceService.class);
  var service=new PaymentOrchestrator(List.of(),ledger,mapper,finance);var customer=new Customer();customer.setCustNo(7L);
  var old=Invoice.builder().id(1L).invoiceNo(1L).build();var next=Invoice.builder().id(2L).invoiceNo(2L).build();
  var in=CustomerTransaction.builder().id(1L).customer(customer).invoice(old).type(CustomerTransactionType.INVOICE_MERGE_IN).amount(new BigDecimal("120.00")).createdAt(OffsetDateTime.now()).build();
  var out=CustomerTransaction.builder().id(2L).customer(customer).invoice(next).type(CustomerTransactionType.INVOICE_MERGE_OUT).amount(new BigDecimal("-120.00")).createdAt(OffsetDateTime.now()).build();
  service.postInvoiceTransactions(List.of(in,out),new User(),customer);
  var entries=ArgumentCaptor.forClass(LedgerRequest.class);verify(ledger,times(2)).createEntry(entries.capture());
  assertEquals(LedgerDirection.IN,entries.getAllValues().get(0).direction());assertEquals(LedgerDirection.OUT,entries.getAllValues().get(1).direction());
  assertEquals(entries.getAllValues().get(0).amount(),entries.getAllValues().get(1).amount());
  assertEquals(1L,entries.getAllValues().get(0).invoiceId());assertEquals(2L,entries.getAllValues().get(1).invoiceId());
 }
}
