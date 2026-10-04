package com.example.baget.crm;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
@RequestMapping("/api/crm/customers/{customer}/branches/{branch}/orders/{order}/issue")
public class CrmIssueController {
 private final CrmIssueService service;
 public CrmIssueController(CrmIssueService service){this.service=service;}
 @GetMapping public Object read(@PathVariable("customer") long customer,@PathVariable("branch") long branch,@PathVariable("order") long order,Authentication a){return service.read(customer,branch,order,a);}
 @PostMapping public Object issue(@PathVariable("customer") long customer,@PathVariable("branch") long branch,@PathVariable("order") long order,@Valid @RequestBody CrmIssueService.Issue r,Authentication a){return service.issue(customer,branch,order,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Операцію вже записано. Оновіть історію видачі"));}
}
