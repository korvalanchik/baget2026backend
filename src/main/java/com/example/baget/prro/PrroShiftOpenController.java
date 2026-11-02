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

    public PrroShiftOpenController(PrroShiftOpenPreparationService preparation,
                                   PrroShiftOpenSubmissionService submission) {
        this.preparation = preparation;
        this.submission = submission;
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
}
