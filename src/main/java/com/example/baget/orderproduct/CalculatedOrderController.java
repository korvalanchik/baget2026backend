package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/calculated-orders")
public class CalculatedOrderController {
    private final CalculatedOrderService service;
    public CalculatedOrderController(CalculatedOrderService service){this.service=service;}
    @PostMapping public CalculatedOrderService.View create(@Valid @RequestBody CalculatedOrderRequest r,Authentication a){return service.create(r,a);}
    @GetMapping("/{id}") public CalculatedOrderService.View read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
    @PutMapping("/{id}") public CalculatedOrderService.View update(@PathVariable("id") long id,@Valid @RequestBody CalculatedOrderRequest r,Authentication a){return service.update(id,r,a);}
    public record Revision(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long expectedRevision) {}
    @PostMapping("/{id}/cancel") public CalculatedOrderService.View cancel(@PathVariable("id") long id,@Valid @RequestBody Revision r,Authentication a){return service.cancel(id,r.expectedRevision(),a);}
    @PostMapping("/{id}/reserve") public CalculatedOrderService.View reserve(@PathVariable("id") long id,Authentication a){return service.refill(id,a);}
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> failure(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
