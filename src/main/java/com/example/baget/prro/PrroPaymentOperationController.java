package com.example.baget.prro;

import com.example.baget.prro.service.PrroPaymentOperationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Protected endpoints for binding a saved Baget payment request to one local
 * PRRO operation. These endpoints do not send documents to DPS.
 */
@RestController
@RequestMapping("/api/prro/payments")
public class PrroPaymentOperationController {
    private final PrroPaymentOperationService service;

    public PrroPaymentOperationController(PrroPaymentOperationService service) {
        this.service = service;
    }

    @GetMapping("/{paymentRequestKey}")
    public PrroPaymentOperationService.PaymentOperationView status(
            @PathVariable @NotBlank @Size(max = 36) String paymentRequestKey,
            @RequestParam @Positive long branchNo,
            Authentication authentication) {
        return service.status(paymentRequestKey, branchNo, authentication);
    }

    @PostMapping("/{paymentRequestKey}/prepare")
    public PrroPaymentOperationService.PaymentOperationView prepare(
            @PathVariable @NotBlank @Size(max = 36) String paymentRequestKey,
            @Valid @RequestBody PrepareRequest request,
            Authentication authentication) {
        return service.prepare(paymentRequestKey, request.branchNo(), authentication);
    }

    public record PrepareRequest(@Positive long branchNo) {}
}
