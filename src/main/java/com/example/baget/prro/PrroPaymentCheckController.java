package com.example.baget.prro;

import com.example.baget.prro.service.PrroPaymentCheckPreparationService;
import com.example.baget.prro.service.PrroPaymentOperationService;
import com.example.baget.prro.service.PrroTestCheckSubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TEST-only fiscalization path for real Baget incoming-payment records.
 * The actual payment remains in Baget's ledger; these endpoints do not alter it.
 */
@RestController
@RequestMapping("/api/prro/payments")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroPaymentCheckController {
    private final PrroPaymentOperationService payments;
    private final PrroPaymentCheckPreparationService preparation;
    private final PrroTestCheckSubmissionService submission;

    public PrroPaymentCheckController(PrroPaymentOperationService payments,
                                      PrroPaymentCheckPreparationService preparation,
                                      PrroTestCheckSubmissionService submission) {
        this.payments = payments;
        this.preparation = preparation;
        this.submission = submission;
    }

    @GetMapping("/recent")
    public List<PrroPaymentOperationService.RecentPayment> recent(
            @RequestParam @Positive long branchNo,
            Authentication authentication) {
        return payments.recentPayments(branchNo, authentication);
    }

    @PostMapping("/{paymentRequestKey}/prepare-check")
    public PrroPaymentCheckPreparationService.PreparationResult prepareCheck(
            @PathVariable @NotBlank @Size(max = 36) String paymentRequestKey,
            @Valid @RequestBody PrroPaymentCheckPreparationService.PrepareRequest request,
            Authentication authentication) {
        return preparation.prepare(paymentRequestKey, request, authentication);
    }

    @PostMapping("/{paymentRequestKey}/submit-check")
    public PrroTestCheckSubmissionService.SubmissionResult submitCheck(
            @PathVariable @NotBlank @Size(max = 36) String paymentRequestKey,
            @Valid @RequestBody PrroTestCheckSubmissionService.SubmitRequest request,
            Authentication authentication) {
        return submission.submitPayment(paymentRequestKey, request, authentication);
    }
}
