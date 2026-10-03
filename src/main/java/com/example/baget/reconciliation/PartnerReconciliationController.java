package com.example.baget.reconciliation;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/partner-reconciliation")
public class PartnerReconciliationController {
 private final PartnerReconciliationService service;private final PartnerReconciliationPdf pdf;
 public PartnerReconciliationController(PartnerReconciliationService service,PartnerReconciliationPdf pdf){this.service=service;this.pdf=pdf;}
 @GetMapping("/options") public Object options(Authentication a){return service.options(a);}
 @GetMapping public Object report(@RequestParam("partner") long partner,@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,Authentication a){return service.report(partner,from,to,a);}
 @GetMapping("/pdf") public ResponseEntity<byte[]> pdf(@RequestParam("partner") long partner,@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,Authentication a) throws Exception {
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=reconciliation-"+partner+"-"+from+"-"+to+".pdf").body(pdf.render(service.report(partner,from,to,a)));
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
