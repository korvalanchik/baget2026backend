package com.example.baget.invoices;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceResource {

    private final InvoiceService invoiceService;

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

    @GetMapping("/collective-invoice/{invoiceNo}")
    public ResponseEntity<CollectiveInvoiceDTO> getCollectiveInvoice(@PathVariable Long invoiceNo) {
        return ResponseEntity.ok(invoiceService.getCollectiveInvoice(invoiceNo));
    }


}
