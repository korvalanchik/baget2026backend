package com.example.baget.calculation.estimate;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.preview.MaterialPreviewController.FieldError;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/calculations/estimate-preview")
public class EstimatePreviewController {
    private final EstimatePreviewService service;
    public EstimatePreviewController(EstimatePreviewService service) { this.service = service; }

    @PostMapping
    public EstimatePreviewResponse preview(@Valid @RequestBody EstimatePreviewRequest request,
                                            Authentication auth) {
        return service.preview(request, auth);
    }

    public record ApiError(String code, String message, String clientItemId, List<FieldError> errors) {}

    @ExceptionHandler(PreviewException.class)
    public ResponseEntity<ApiError> handlePreview(PreviewException ex) {
        String id = ex instanceof EstimateItemException item ? item.getClientItemId() : null;
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage(), id, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Перевірте поля кошторису", null, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", "Некоректний JSON або тип значення", null, List.of()));
    }
}
