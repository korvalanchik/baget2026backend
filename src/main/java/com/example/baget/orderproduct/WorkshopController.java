package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/calculated-orders/{orderNo}/workshop")
public class WorkshopController {
    private final WorkshopService service;
    public WorkshopController(WorkshopService service) { this.service=service; }
    @GetMapping public WorkshopService.View read(@PathVariable("orderNo") long orderNo, Authentication a) {
        return service.view(orderNo,a);
    }
    @PutMapping("/{productId}") public WorkshopService.View save(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId, @Valid @RequestBody WorkshopService.Correction r, Authentication a) {
        return service.save(orderNo,productId,r,a);
    }
    @PostMapping("/{productId}/state") public WorkshopService.View state(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId, @Valid @RequestBody WorkshopService.Transition r, Authentication a) {
        return service.transition(orderNo,productId,r,a);
    }
    @PostMapping("/{productId}/complete") public WorkshopService.View complete(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId, @Valid @RequestBody CalculatedOrderController.Revision r, Authentication a) {
        return service.complete(orderNo,productId,r.expectedRevision(),a);
    }
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> failure(PreviewException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));
    }
}
