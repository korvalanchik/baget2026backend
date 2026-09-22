package com.example.baget.order_counter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class TempOrderCounterResource {
    private final TempOrderCounterService tempOrderCounterService;
    public TempOrderCounterResource(TempOrderCounterService tempOrderCounterService) {
        this.tempOrderCounterService = tempOrderCounterService;
    }

    @GetMapping("/next-order-number")
    public ResponseEntity<Long> getNextOrderNumber() {
        Long nextOrderNumber = tempOrderCounterService.getNextOrderNumber();
        return ResponseEntity.ok(nextOrderNumber);
    }
}
