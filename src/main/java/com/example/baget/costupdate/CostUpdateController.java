package com.example.baget.costupdate;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/warehouse/cost-update")
public class CostUpdateController {
 private final CostUpdateService service;
 public CostUpdateController(CostUpdateService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(q,page,a);}
 @PostMapping("/rate") public Object rate(@Valid @RequestBody CostUpdateService.Rate r,Authentication a){return service.rate(r,a);}
 @PostMapping("/apply") public Object apply(@Valid @RequestBody CostUpdateService.Apply r,Authentication a){return service.apply(r,a);}
 @GetMapping("/history") public Object history(@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.history(page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
