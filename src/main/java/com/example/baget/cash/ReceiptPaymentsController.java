package com.example.baget.cash;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/cash/receipt-payments")
public class ReceiptPaymentsController {
 private final ReceiptPaymentsService service;
 public ReceiptPaymentsController(ReceiptPaymentsService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(q,page,a);}
 @GetMapping("/{id}") public Object read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
 @PostMapping("/{id}") public Object pay(@PathVariable("id") long id,@RequestBody @Valid ReceiptPaymentsService.Pay r,Authentication a){return service.pay(id,r,a);}
 @PostMapping("/{id}/reverse") public Object reverse(@PathVariable("id") long id,@RequestBody @Valid ReceiptPaymentsService.Reverse r,Authentication a){return service.reverse(id,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
