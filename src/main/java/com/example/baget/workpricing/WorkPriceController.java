package com.example.baget.workpricing;

import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/work-prices")
public class WorkPriceController {
    private final WorkPriceService service;

    public WorkPriceController(WorkPriceService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(name = "page", defaultValue = "0") int page,
        Authentication authentication
    ) {
        return service.list(query, page, authentication);
    }

    @PutMapping("/{partNo}")
    public Object update(
        @PathVariable @Positive long partNo,
        @Valid @RequestBody WorkPriceService.PriceUpdate request,
        Authentication authentication
    ) {
        return service.update(partNo, request, authentication);
    }

    @ExceptionHandler(PreviewException.class)
    public ResponseEntity<?> error(PreviewException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(Map.of("message", exception.getMessage(), "code", exception.getCode()));
    }
}
