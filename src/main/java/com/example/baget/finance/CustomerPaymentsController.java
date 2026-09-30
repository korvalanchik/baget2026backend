package com.example.baget.finance;
import com.example.baget.customer.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/customer-payments")
public class CustomerPaymentsController {
    private final CustomerPayments service;
    public CustomerPaymentsController(CustomerPayments service){this.service=service;}
    @GetMapping("/orders/{order}/context") public java.util.Map<String,Object> context(@PathVariable long order,Authentication a){return service.orderContext(order,a);}
    @GetMapping("/{customer}") public CustomerFinanceDTO read(@PathVariable long customer,@RequestParam long branch,Authentication a){return service.read(customer,branch,a);}
    @PostMapping("/{customer}") public com.fasterxml.jackson.databind.JsonNode pay(@PathVariable long customer,@Valid @RequestBody CustomerPayments.Payment r,Authentication a){return service.pay(customer,r,a);}
}
