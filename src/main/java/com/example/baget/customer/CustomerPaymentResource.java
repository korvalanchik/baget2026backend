package com.example.baget.customer;

import com.example.baget.finance.InvoiceSettlementService;
import com.example.baget.invoices.InvoicePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
public class CustomerPaymentResource {

    private final CustomerPaymentService customerPaymentService;
    private final CustomerTransactionService customerTransactionService;
    private final InvoiceSettlementService invoiceSettlementService;

    @PostMapping("/invoices/payments")
    public ResponseEntity<List<CustomerTransactionDTO>> addPayment(
            @RequestBody InvoicePaymentRequest request,
            Authentication authentication
    ) {
        List<CustomerTransactionDTO> dto = invoiceSettlementService.settleInvoice(request, authentication);

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/balance")
    public BigDecimal getBalance(@PathVariable Long id) {
        return customerTransactionService.getCustomerBalance(id);
    }

    @GetMapping("/payments")
    public List<CustomerPaymentDTO> getPayments(
            @RequestParam Long invoiceId
    ) {
        return customerPaymentService.getPaymentsByInvoice(invoiceId);
    }

    @GetMapping("/{customerId}/finance")
    public CustomerFinanceDTO getFinance(@PathVariable Long customerId) {
        return customerPaymentService.getCustomerFinance(customerId);
    }

    @PostMapping("/advance")
    public ResponseEntity<List<CustomerTransactionDTO>> addAdvance(
            @RequestBody InvoicePaymentRequest request,
            Authentication authentication
    ) {
        // log.info("Payment request: {}", request);
        List<CustomerTransactionDTO> dto = invoiceSettlementService.settleAdvance(request, authentication);

        return ResponseEntity.ok(dto);
    }
}
