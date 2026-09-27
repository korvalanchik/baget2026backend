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
    private final ReworkService reworks;
    private final WorkshopCalculationService calculations;
    public WorkshopController(WorkshopService service,ReworkService reworks,WorkshopCalculationService calculations) {
        this.service=service; this.reworks=reworks; this.calculations=calculations;
    }
    @PostMapping("/{productId}/calculate") public WorkshopCalculationService.Result calculate(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId,@Valid @RequestBody WorkshopCalculationService.Request r,Authentication a) {
        return calculations.calculate(orderNo,productId,r,a);
    }
    @GetMapping("/rework-catalog") public Object catalog(@PathVariable("orderNo") long orderNo,
            @RequestParam(name="q",defaultValue="") String q,Authentication a) {
        return reworks.catalog(orderNo,q,a);
    }
    @PostMapping("/{productId}/rework") public WorkshopService.View rework(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId,@Valid @RequestBody ReworkService.Open r,Authentication a) {
        reworks.open(orderNo,productId,r,a);return service.view(orderNo,a);
    }
    @PutMapping("/{productId}/rework") public WorkshopService.View saveRework(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId,@Valid @RequestBody ReworkService.Save r,Authentication a) {
        reworks.save(orderNo,productId,r,a);return service.view(orderNo,a);
    }
    @PostMapping("/{productId}/rework/complete") public WorkshopService.View completeRework(@PathVariable("orderNo") long orderNo,
            @PathVariable("productId") long productId,@Valid @RequestBody CalculatedOrderController.Revision r,Authentication a) {
        reworks.complete(orderNo,productId,r.expectedRevision(),a);return service.view(orderNo,a);
    }
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
