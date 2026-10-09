package com.example.baget.cash;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/cash/expenses")
public class CashExpensesController {
 private final CashExpensesService service;
 public CashExpensesController(CashExpensesService service){this.service=service;}
 @GetMapping("/options") public Object options(Authentication a){return service.options(a);}
 @GetMapping public Object list(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="accountId",required=false) Long accountId,@RequestParam(name="category",defaultValue="") String category,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(from,to,accountId,category,page,a);}
 @PostMapping public Object spend(@RequestBody @Valid CashExpensesService.Spend r,Authentication a){return service.spend(r,a);}
 @PostMapping("/{id}/reverse") public Object reverse(@PathVariable("id") long id,@RequestBody @Valid CashExpensesService.Reverse r,Authentication a){return service.reverse(id,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
