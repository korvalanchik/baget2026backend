package com.example.baget.invoices;

import com.example.baget.billing.BillingAccess;
import com.example.baget.billing.ProductInvoicePdf;
import com.example.baget.billing.ProductInvoiceProcessor;
import com.example.baget.util.TransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {
    private final JdbcTemplate db;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final BillingAccess billingAccess;
    private final ProductInvoiceProcessor productInvoiceProcessor;
    private final ProductInvoicePdf productInvoicePdf;
    private final LegacyInvoicePdf legacyInvoicePdf;

    public record PdfDocument(Long invoiceNo, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PdfDocument render(long invoiceId, Authentication authentication) throws Exception {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new TransactionException("Рахунок не знайдено"));

        long productLines = db.queryForObject(
                "SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",
                Long.class, invoiceId);

        if (productLines > 0) {
            var user = billingAccess.customerInvoiceUser(authentication);
            billingAccess.invoice(user, invoiceId);
            var data = productInvoiceProcessor.readForCustomerCard(invoiceId, authentication);
            return new PdfDocument(invoice.getInvoiceNo(), productInvoicePdf.render(data));
        }

        var user = billingAccess.customerInvoiceUser(authentication);
        billingAccess.invoice(user, invoiceId);
        InvoiceViewDTO view = invoiceService.getInvoice(invoiceId);
        BigDecimal debt = invoiceRepository.calculateInvoiceDebt(invoiceId);
        byte[] contents = legacyInvoicePdf.render(view,
                invoice.getEffectivePayer().getCompany(),
                invoice.getNote(),
                invoice.getLifecycle() == null ? null : invoice.getLifecycle().name(),
                debt);
        return new PdfDocument(invoice.getInvoiceNo(), contents);
    }
}