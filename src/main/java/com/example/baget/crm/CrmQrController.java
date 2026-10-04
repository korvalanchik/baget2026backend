package com.example.baget.crm;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
@RequestMapping("/api/crm/issue-qr")
public class CrmQrController {
 private final CrmIssueService service;
 public CrmQrController(CrmIssueService service){this.service=service;}
 @GetMapping("/{token}") public Object resolve(@PathVariable("token") String token,Authentication a){return service.resolveQr(token,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
}
