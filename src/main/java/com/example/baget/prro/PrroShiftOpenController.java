package com.example.baget.prro;

import com.example.baget.prro.service.PrroShiftOpenPreparationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prepares a shift-open XML envelope for signing in the cashier's IIT widget.
 * This endpoint does not sign or submit documents to DPS.
 */
@RestController
@RequestMapping("/api/prro/shift/open")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftOpenController {

    private final PrroShiftOpenPreparationService service;

    public PrroShiftOpenController(PrroShiftOpenPreparationService service) {
        this.service = service;
    }

    @PostMapping("/prepare")
    public PrroShiftOpenPreparationService.PreparationResult prepare(
            @Valid @RequestBody PrroShiftOpenPreparationService.PrepareRequest request,
            Authentication authentication) {
        return service.prepare(request, authentication);
    }
}
