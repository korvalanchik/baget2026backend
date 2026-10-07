package com.example.baget.reports;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/reports/owner-results")
public class OwnerResultsController {
 private final OwnerResultsService service;
 public OwnerResultsController(OwnerResultsService service){this.service=service;}
 @GetMapping("/branches") public Object branches(Authentication a){return service.branches(a);}
 @GetMapping public Object report(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.report(from,to,branch,page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
