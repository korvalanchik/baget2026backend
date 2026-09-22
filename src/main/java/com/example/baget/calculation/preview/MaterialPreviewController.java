package com.example.baget.calculation.preview;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/calculations/material-preview")
public class MaterialPreviewController {
    private final MaterialPreviewService service;
    public MaterialPreviewController(MaterialPreviewService service) { this.service = service; }

    @PostMapping
    public MaterialPreviewResponse preview(@Valid @RequestBody MaterialPreviewRequest request,
                                           Authentication authentication) {
        return service.preview(request, authentication);
    }

    public record FieldError(String field, String message) {}
    public record ApiError(String code, String message, List<FieldError> errors) {}

    // Controller-local handlers leave existing endpoints and advice unchanged.
    @ExceptionHandler(PreviewException.class)
    public ResponseEntity<ApiError> handlePreview(PreviewException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Перевірте поля запиту", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", "Некоректний JSON або тип значення", List.of()));
    }
}
