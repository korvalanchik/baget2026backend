package com.example.baget.crm;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/crm")
public class CrmController {
 private final CrmService service;
 public CrmController(CrmService service){this.service=service;}
 @GetMapping("/branches") public Object branches(Authentication a){return service.branches(a);}
 @GetMapping("/customers") public Object list(@RequestParam(name="branch",required=false) Long branch,
  @RequestParam(name="tab",defaultValue="WORK") String tab,@RequestParam(name="q",defaultValue="") String q,
  @RequestParam(name="all",defaultValue="false") boolean all,@RequestParam(name="due",defaultValue="false") boolean due,
  @RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(branch,tab,q,all,due,page,a);}
 @GetMapping("/customers/{id}/branches/{branch}") public Object detail(@PathVariable("id") long id,@PathVariable("branch") long branch,
  @RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.detail(id,branch,page,a);}
 @PostMapping("/customers/{id}/branches/{branch}/contacts") public Object contact(@PathVariable("id") long id,@PathVariable("branch") long branch,
  @Valid @RequestBody CrmService.Contact r,Authentication a){return service.contact(id,branch,r,a);}
 @PostMapping("/customers/{id}/branches/{branch}/partner") public Object partner(@PathVariable("id") long id,@PathVariable("branch") long branch,
  @Valid @RequestBody CrmService.Partner r,Authentication a){return service.partner(id,branch,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(java.util.Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(java.util.Map.of("message","Ключ операції вже використано. Оновіть картку."));}
}
