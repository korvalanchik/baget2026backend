package com.example.baget.invoices;

import com.example.baget.customer.CustomerTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InvoiceFinanceService {

    private final CustomerTransactionRepository customerTxRepository;
    private final InvoiceRepository invoiceRepository;
    private final com.example.baget.ledger.LedgerRepository ledgerRepository;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    public boolean usesProductionLedger(Invoice invoice) {
        return db.queryForObject("SELECT COUNT(*) FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=? AND o.calculation_revision>0",Long.class,invoice.getId())>0;
    }

    public BigDecimal calculateInvoiceDebt(Invoice invoice) {

        if(usesProductionLedger(invoice)) return ledgerRepository.calculateInvoiceDebt(invoice.getId()).max(BigDecimal.ZERO);
        Long invoiceId = invoice.getId();

        BigDecimal paid = customerTxRepository.totalPayments(invoiceId);

        BigDecimal allocated = customerTxRepository.totalAdvanceAllocated(invoiceId);

        return invoice.getTotalAmount()
                .subtract(paid)
                .subtract(allocated)
                .max(BigDecimal.ZERO);
    }

    @Transactional
    public void refreshInvoiceStatus(Invoice invoice) {

        BigDecimal debt =
                calculateInvoiceDebt(invoice);

        if (debt.compareTo(BigDecimal.ZERO) == 0) {

            invoice.setStatus(
                    InvoiceEnums.InvoiceStatus.PAID);

        } else if (
                debt.compareTo(invoice.getTotalAmount()) < 0) {

            invoice.setStatus(
                    InvoiceEnums.InvoiceStatus.PARTIALLY_PAID);

        } else {

            invoice.setStatus(
                    InvoiceEnums.InvoiceStatus.ISSUED);
        }

        invoiceRepository.save(invoice);
    }
}