package com.example.baget.cash;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/cash/flow")
public class CashFlowController {
 private final CashFlowService service;
 public CashFlowController(CashFlowService service){this.service=service;}
 @GetMapping public Object report(@RequestParam(name="from",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(name="to",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,Authentication a){return service.report(from,to,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
