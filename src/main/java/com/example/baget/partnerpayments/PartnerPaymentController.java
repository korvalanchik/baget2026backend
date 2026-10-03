package com.example.baget.partnerpayments;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
@RequestMapping("/api/partner-payments")
public class PartnerPaymentController {
 private final PartnerPaymentService service;
 public PartnerPaymentController(PartnerPaymentService service){this.service=service;}
 @GetMapping("/context") public Object context(@RequestParam(name="order",required=false) Long order,@RequestParam(name="customer",required=false) Long customer,@RequestParam(name="branch",required=false) Long branch,Authentication a){return service.context(order,customer,branch,a);}
 @GetMapping("/{customer}") public Object read(@PathVariable("customer") long customer,@RequestParam("branch") long branch,@RequestParam("partner") long partner,@RequestParam(name="contextOrder",required=false) Long contextOrder,@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="historyPage",defaultValue="0") int historyPage,Authentication a){return service.read(customer,branch,partner,contextOrder,page,historyPage,a);}
 @PostMapping("/{customer}") public Object pay(@PathVariable("customer") long customer,@Valid @RequestBody PartnerPaymentService.Payment r,Authentication a){return service.pay(customer,r,a);}
 @PostMapping("/{customer}/adjustments") public Object adjust(@PathVariable("customer") long customer,@Valid @RequestBody PartnerPaymentService.Adjustment r,Authentication a){return service.adjust(customer,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> conflict(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Клієнта, філію або замовлення не знайдено"));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Ключ операції вже використаний. Оновіть історію та перевірте платіж"));}
}
