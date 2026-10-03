package com.example.baget.billing;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
@RequestMapping("/api/partner-receivables")
public class PartnerReceivablesController {
 private final PartnerReceivablesService service;
 public PartnerReceivablesController(PartnerReceivablesService service){this.service=service;}
 @GetMapping public Object read(@RequestParam(name="partner",required=false) Long partner,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.read(partner,page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
