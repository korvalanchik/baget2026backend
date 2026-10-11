package com.example.baget.cash;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/cash/day-end")
public class CashDayEndController {
 private final CashDayEndService service;
 public CashDayEndController(CashDayEndService service){this.service=service;}
 @GetMapping public Object read(@RequestParam(name="day",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate day,Authentication a){return service.read(day,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
