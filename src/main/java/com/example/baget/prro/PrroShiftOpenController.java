package com.example.baget.prro;

import com.example.baget.prro.service.PrroShiftOpenPreparationService;
import com.example.baget.prro.service.PrroShiftOpenSubmissionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prro/shift/open")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftOpenController {
    private final PrroShiftOpenPreparationService preparation;
    private final PrroShiftOpenSubmissionService submission;
    private final PrroShiftOpenCancellationService cancellation;

    public PrroShiftOpenController(PrroShiftOpenPreparationService preparation,
                                   PrroShiftOpenSubmissionService submission,
                                   PrroShiftOpenCancellationService cancellation) {
        this.preparation = preparation;
        this.submission = submission;
        this.cancellation = cancellation;
    }

    @PostMapping("/prepare")
    public PrroShiftOpenPreparationService.PreparationResult prepare(
            @Valid @RequestBody PrroShiftOpenPreparationService.PrepareRequest request,
            Authentication authentication) {
        return preparation.prepare(request, authentication);
    }

    @PostMapping("/submit")
    public PrroShiftOpenSubmissionService.SubmissionResult submit(
            @Valid @RequestBody PrroShiftOpenSubmissionService.SubmitRequest request,
            Authentication authentication) {
        return submission.submit(request, authentication);
    }

    @PostMapping("/cancel-prepared")
    public PrroShiftOpenCancellationService.CancellationResult cancelPrepared(
            @Valid @RequestBody PrroShiftOpenCancellationService.CancelRequest request,
            Authentication authentication) {
        return cancellation.cancel(request, authentication);
    }
}
