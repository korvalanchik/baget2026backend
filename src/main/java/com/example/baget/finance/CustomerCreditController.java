package com.example.baget.finance;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/customer-credit")
public class CustomerCreditController {
 private final CustomerCreditService service;
 public CustomerCreditController(CustomerCreditService service){this.service=service;}
 @GetMapping("/{customer}/branches/{branch}") public Object read(@PathVariable long customer,@PathVariable long branch,Authentication a){return service.read(customer,branch,a);}
 @PostMapping("/{customer}/branches/{branch}/policy") public Object policy(@PathVariable long customer,@PathVariable long branch,@Valid @RequestBody CustomerCreditService.Policy r,Authentication a){return service.savePolicy(customer,branch,r,a);}
 @PostMapping("/{customer}/branches/{branch}/invoices/{invoice}/terms") public Object terms(@PathVariable long customer,@PathVariable long branch,@PathVariable long invoice,@Valid @RequestBody CustomerCreditService.Terms r,Authentication a){return service.saveTerms(customer,branch,invoice,r,a);}
 @GetMapping("/receivables") public Object debts(@RequestParam(required=false) Long branch,@RequestParam(defaultValue="false") boolean overdue,@RequestParam(defaultValue="0") int page,Authentication a){return service.receivables(branch,overdue,page,a);}
}
