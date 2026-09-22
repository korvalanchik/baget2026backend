package com.example.baget.finance;
import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.ledger.*;
import com.example.baget.users.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ProductionFinanceTest {
    @Test void newInvoiceDebtComesFromLedgerNotStatisticalFieldsOrUiTransactions() {
        var ct=mock(CustomerTransactionRepository.class);
        var repo=mock(InvoiceRepository.class);
        var ledger=mock(LedgerRepository.class);
        var db=mock(JdbcTemplate.class);
        var invoice=Invoice.builder().id(42L).totalAmount(new BigDecimal("900")).build();
        when(db.queryForObject(anyString(),eq(Long.class),eq(42L))).thenReturn(1L);
        when(ledger.calculateInvoiceDebt(42L)).thenReturn(new BigDecimal("300"));
        var service=new InvoiceFinanceService(ct,repo,ledger,db);
        assertEquals(new BigDecimal("300"),service.calculateInvoiceDebt(invoice));
        verifyNoInteractions(ct);
    }
    @Test void historicalInvoiceKeepsExistingDebtCalculationUntilReconciliation() {
        var ct=mock(CustomerTransactionRepository.class);
        var repo=mock(InvoiceRepository.class);
        var ledger=mock(LedgerRepository.class);
        var db=mock(JdbcTemplate.class);
        var invoice=Invoice.builder().id(42L).totalAmount(new BigDecimal("900")).build();
        when(db.queryForObject(anyString(),eq(Long.class),eq(42L))).thenReturn(0L);
        when(ct.totalPayments(42L)).thenReturn(new BigDecimal("200"));
        when(ct.totalAdvanceAllocated(42L)).thenReturn(new BigDecimal("100"));
        var service=new InvoiceFinanceService(ct,repo,ledger,db);
        assertEquals(new BigDecimal("600"),service.calculateInvoiceDebt(invoice));
        verifyNoInteractions(ledger);
    }
    @Test void surplusAdvanceIsNotCreditedToInvoiceAndStatusRefreshFollowsLedger() {
        var processor=mock(PaymentProcessor.class);
        var ledger=mock(LedgerService.class);
        var mapper=mock(CustomerTransactionMapper.class);
        var finance=mock(InvoiceFinanceService.class);
        var customer=new Customer(); customer.setCustNo(7L);
        var invoice=Invoice.builder().id(42L).invoiceNo(21092026001L).build();
        var request=new InvoicePaymentRequest(1L,42L,7L,new BigDecimal("120"),BigDecimal.ZERO,"");
        var ctx=new InvoiceOperationContext(new User(),null,customer,customer,invoice);
        var payment=CustomerTransaction.builder().id(1L).type(CustomerTransactionType.PAYMENT).amount(new BigDecimal("100")).build();
        var advance=CustomerTransaction.builder().id(2L).type(CustomerTransactionType.ADVANCE).amount(new BigDecimal("20")).build();
        when(processor.supports(request)).thenReturn(true);
        when(processor.process(eq(ctx),eq(request),any())).thenReturn(List.of(payment,advance));
        var service=new PaymentOrchestrator(List.of(processor),ledger,mapper,finance);
        service.processPayment(ctx,request,ctx.user());
        var entries=ArgumentCaptor.forClass(LedgerRequest.class);
        verify(ledger,times(2)).createEntry(entries.capture());
        assertEquals(42L,entries.getAllValues().get(0).invoiceId());
        assertNull(entries.getAllValues().get(1).invoiceId());
        var order=inOrder(ledger,finance);
        order.verify(ledger,times(2)).createEntry(any());
        order.verify(finance).refreshInvoiceStatus(invoice);
    }
}
