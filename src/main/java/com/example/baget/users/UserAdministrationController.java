package com.example.baget.users;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {
 private final UserAdministrationService service;
 public UserAdministrationController(UserAdministrationService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(q,page,a);}
 @GetMapping("/options") public Object options(Authentication a){return service.options(a);}
 @PostMapping public Object save(@Valid @RequestBody UserAdministrationService.Edit e,Authentication a){return service.save(e,a);}
 @GetMapping("/{id}/history") public Object history(@PathVariable("id") long id,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.history(id,page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Логін або електронна адреса вже використовується. Оновіть список."));}
}
