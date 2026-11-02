package com.example.baget.prro;

import com.example.baget.prro.service.PrroShiftClosePreparationService;
import com.example.baget.prro.service.PrroShiftCloseSubmissionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prro/shift/close")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftCloseController {
    private final PrroShiftClosePreparationService preparation;
    private final PrroShiftCloseSubmissionService submission;

    public PrroShiftCloseController(PrroShiftClosePreparationService preparation,
                                    PrroShiftCloseSubmissionService submission) {
        this.preparation = preparation;
        this.submission = submission;
    }

    @PostMapping("/prepare")
    public PrroShiftClosePreparationService.PreparationResult prepare(
            @Valid @RequestBody PrroShiftClosePreparationService.PrepareRequest request,
            Authentication authentication) {
        return preparation.prepare(request, authentication);
    }

    @PostMapping("/submit")
    public PrroShiftCloseSubmissionService.SubmissionResult submit(
            @Valid @RequestBody PrroShiftCloseSubmissionService.SubmitRequest request,
            Authentication authentication) {
        return submission.submit(request, authentication);
    }
}
