package com.example.baget.openingcost;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/warehouse/materials/{part}/opening-cost")
public class OpeningCostController {
 private final OpeningCostService service;
 public OpeningCostController(OpeningCostService service){this.service=service;}
 @GetMapping public Object read(@PathVariable("part") long part,Authentication a){return service.read(part,a);}
 @GetMapping("/history") public Object history(@PathVariable("part") long part,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.history(part,page,a);}
 @PostMapping public Object save(@PathVariable("part") long part,@Valid @RequestBody OpeningCostService.Request r,Authentication a){return service.save(part,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Ключ запиту вже використано. Перевірте історію встановлення собівартості."));}
}
