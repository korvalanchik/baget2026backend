package com.example.baget.orderproduct;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/calculated-orders/{id}/production")
public class ProductionController {
    private final ProductionService service;
    public ProductionController(ProductionService service){this.service=service;}
    @GetMapping public ProductionService.View read(@PathVariable("id") long id,Authentication a){return service.view(id,a);}
    @PutMapping public ProductionService.View save(@PathVariable("id") long id,@Valid @RequestBody ProductionService.Actuals r,Authentication a){return service.save(id,r,a);}
    @PostMapping("/status") public ProductionService.View status(@PathVariable("id") long id,@Valid @RequestBody ProductionService.Transition r,Authentication a){return service.transition(id,r,a);}
    @PostMapping("/finalize") public ProductionService.View finish(@PathVariable("id") long id,@Valid @RequestBody CalculatedOrderController.Revision r,Authentication a){return service.finalizeProduction(id,r.expectedRevision(),a);}
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> failure(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
