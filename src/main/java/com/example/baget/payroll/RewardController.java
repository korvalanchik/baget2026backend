package com.example.baget.payroll;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@RestController
@RequestMapping("/api/rewards")
public class RewardController {
 private final RewardService service;public RewardController(RewardService service){this.service=service;}
 @GetMapping public Object accounts(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,Authentication a){return service.accounts(from,to,a);}
 @GetMapping("/settings") public Object settings(Authentication a){return service.settings(a);}
 @PostMapping("/settings") public Object settings(@Valid @RequestBody RewardService.Settings r,Authentication a){return service.saveSettings(r,a);}
 @GetMapping("/accounts/{id}") public Object history(@PathVariable("id") long id,@RequestParam(name="page",defaultValue="0") int page,@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,Authentication a){return service.history(id,page,from,to,a);}
 @PostMapping("/entries") public Object post(@Valid @RequestBody RewardService.Operation r,Authentication a){return service.post(r,a);}
 @GetMapping("/review/users") public Object reviewUsers(Authentication a){return service.reviewUsers(a);}
 @GetMapping("/review") public Object review(Authentication a){return service.review(a);}
 @PostMapping("/review") public Object review(@Valid @RequestBody RewardService.Resolve r,Authentication a){return service.resolve(r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Операцію вже записано або дані змінилися. Оновіть журнал."));}
}
