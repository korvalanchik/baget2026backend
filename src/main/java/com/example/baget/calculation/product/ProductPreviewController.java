package com.example.baget.calculation.product;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.preview.MaterialPreviewController.ApiError;
import com.example.baget.calculation.preview.MaterialPreviewController.FieldError;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/calculations/product-preview")
public class ProductPreviewController {
    private final ProductPreviewService service;
    public ProductPreviewController(ProductPreviewService service) { this.service = service; }

    @PostMapping
    public ProductPreviewResponse preview(@Valid @RequestBody ProductPreviewRequest request,
                                           Authentication authentication) {
        return service.preview(request, authentication);
    }
    @ExceptionHandler(PreviewException.class)
    public ResponseEntity<ApiError> handlePreview(PreviewException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage(), List.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Перевірте поля виробу", fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", "Некоректний JSON або тип виробу", List.of()));
    }
}
