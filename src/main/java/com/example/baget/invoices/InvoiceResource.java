package com.example.baget.invoices;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceResource {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    @PostMapping("/merge")
    public ResponseEntity<InvoiceDTO> createInvoice(
            @RequestBody MergeInvoicesRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(invoiceService.mergeInvoices(request, authentication));
    }

    @GetMapping("/{id}")
    public InvoiceViewDTO getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoice(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable Long id, Authentication authentication) throws Exception {
        InvoicePdfService.PdfDocument pdf = invoicePdfService.render(id, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice_" + pdf.invoiceNo() + ".pdf")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.bytes());
    }

    @GetMapping("/collective-invoice/{invoiceNo}")
    public ResponseEntity<CollectiveInvoiceDTO> getCollectiveInvoice(@PathVariable Long invoiceNo) {
        return ResponseEntity.ok(invoiceService.getCollectiveInvoice(invoiceNo));
    }
}
