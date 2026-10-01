package com.example.baget.commercial;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/commercial")
public class CommercialController {
 private final CommercialService service;
 public CommercialController(CommercialService service){this.service=service;}
 @GetMapping("/branches") public Object branches(Authentication a){return service.branches(a);}
 @PutMapping("/branches/{branch}") public Object configure(@PathVariable long branch,@Valid @RequestBody CommercialService.Settings r,Authentication a){return service.configure(branch,r,a);}
 @GetMapping("/pending") public Object pending(Authentication a){return service.pending(a);}
 @GetMapping("/products/{product}") public Object read(@PathVariable long product,Authentication a){return service.read(product,a);}
 @PostMapping("/products/{product}/decision") public Object decide(@PathVariable long product,@Valid @RequestBody CommercialService.Decision r,Authentication a){return service.decide(product,r,a);}
}
