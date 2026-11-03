package com.example.baget.prro;

import com.example.baget.prro.service.PrroTestCheckPreparationService;
import com.example.baget.prro.service.PrroTestCheckSubmissionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prro/test-check")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroTestCheckController {
    private final PrroTestCheckPreparationService preparation;
    private final PrroTestCheckSubmissionService submission;

    public PrroTestCheckController(PrroTestCheckPreparationService preparation,
                                   PrroTestCheckSubmissionService submission) {
        this.preparation = preparation;
        this.submission = submission;
    }

    @PostMapping("/prepare")
    public PrroTestCheckPreparationService.PreparationResult prepare(
            @Valid @RequestBody PrroTestCheckPreparationService.PrepareRequest request,
            Authentication authentication) {
        return preparation.prepare(request, authentication);
    }

    @PostMapping("/submit")
    public PrroTestCheckSubmissionService.SubmissionResult submit(
            @Valid @RequestBody PrroTestCheckSubmissionService.SubmitRequest request,
            Authentication authentication) {
        return submission.submit(request, authentication);
    }
}
