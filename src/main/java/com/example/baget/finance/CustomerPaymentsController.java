package com.example.baget.finance;
import com.example.baget.customer.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/customer-payments")
public class CustomerPaymentsController {
    private final CustomerPayments service;
    private final CustomerPaymentReceiptService receipts;
    public CustomerPaymentsController(CustomerPayments service,CustomerPaymentReceiptService receipts){this.service=service;this.receipts=receipts;}
    @GetMapping("/orders/{order}/context") public java.util.Map<String,Object> context(@PathVariable long order,Authentication a){return service.orderContext(order,a);}
    @GetMapping("/receipts/{requestKey}/pdf")
    public ResponseEntity<byte[]> receipt(@PathVariable String requestKey,Authentication a) throws Exception {
        var pdf=receipts.render(requestKey,a);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=receipt_"+pdf.receiptNumber()+".pdf")
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(pdf.bytes());
    }
    @GetMapping("/{customer}") public CustomerFinanceDTO read(@PathVariable long customer,@RequestParam long branch,Authentication a){return service.read(customer,branch,a);}
    @PostMapping("/{customer}") public com.fasterxml.jackson.databind.JsonNode pay(@PathVariable long customer,@Valid @RequestBody CustomerPayments.Payment r,Authentication a){return service.pay(customer,r,a);}
}
