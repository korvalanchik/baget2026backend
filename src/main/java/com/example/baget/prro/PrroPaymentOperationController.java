package com.example.baget.prro;

import com.example.baget.prro.service.PrroPaymentOperationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Protected endpoints for binding an already-recorded Baget incoming payment
 * to one local PRRO operation. These endpoints do not send documents to DPS.
 */
@RestController
@RequestMapping("/api/prro/payments")
public class PrroPaymentOperationController {
    private final PrroPaymentOperationService service;

    public PrroPaymentOperationController(PrroPaymentOperationService service) {
        this.service = service;
    }

    @GetMapping("/{transactionId}")
    public PrroPaymentOperationService.PaymentOperationView status(
            @PathVariable @Positive long transactionId,
            @RequestParam @Positive long branchNo,
            Authentication authentication) {
        return service.status(transactionId, branchNo, authentication);
    }

    @PostMapping("/{transactionId}/prepare")
    public PrroPaymentOperationService.PaymentOperationView prepare(
            @PathVariable @Positive long transactionId,
            @Valid @RequestBody PrepareRequest request,
            Authentication authentication) {
        return service.prepare(transactionId, request.branchNo(), authentication);
    }

    public record PrepareRequest(@Positive long branchNo) {}
}
