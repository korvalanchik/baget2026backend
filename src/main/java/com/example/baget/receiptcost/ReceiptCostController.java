package com.example.baget.receiptcost;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/warehouse/priced-receipts")
public class ReceiptCostController {
 private final ReceiptCostService service;
 public ReceiptCostController(ReceiptCostService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(page,a);}
 @GetMapping("/{id}") public Object read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
 @GetMapping("/by-key/{key}") public Object byKey(@PathVariable("key") String key,Authentication a){return service.byKey(key,a);}
 @GetMapping("/materials/{part}") public Object material(@PathVariable("part") long part,Authentication a){return service.material(part,a);}
 @PostMapping("/preview") public Object preview(@Valid @RequestBody ReceiptCostService.Request r,Authentication a){return service.preview(r,a);}
 @PostMapping public Object post(@Valid @RequestBody ReceiptCostService.Post r,Authentication a){return service.post(r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Документ або матеріал не знайдено"));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Накладна або ключ уже існує. Оновіть журнал; не проводьте прихід повторно."));}
}
