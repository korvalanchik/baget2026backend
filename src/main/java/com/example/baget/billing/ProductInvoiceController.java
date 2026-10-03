package com.example.baget.billing;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/product-invoices")
public class ProductInvoiceController {
 private final ProductInvoiceProcessor service;
 private final ProductInvoicePdf pdf;
 @GetMapping("/options") public Object options(@RequestParam(name="q",defaultValue="") String q,Authentication a){return service.options(q,a);}
 @GetMapping("/ready") public Object ready(@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="partnerId",required=false) Long partnerId,Authentication a){return service.ready(page,partnerId,a);}
 @GetMapping public Object list(@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(page,a);}
 @GetMapping("/{id}") public Object read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
 @PostMapping public Object issue(@Valid @RequestBody ProductInvoiceProcessor.Issue r,Authentication a){return service.issue(r,a);}
 @PostMapping("/merge") public Object merge(@Valid @RequestBody ProductInvoiceProcessor.Merge r,Authentication a){return service.merge(r,a);}
 @PostMapping("/{id}/payment") public Object payment(@PathVariable("id") long id,@Valid @RequestBody ProductInvoiceProcessor.Payment r,Authentication a){return service.pay(id,r,a);}
 @GetMapping("/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable("id") long id,Authentication a) throws Exception {
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=invoice_"+id+".pdf").body(pdf.render(service.read(id,a)));
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Операцію вже виконано або виріб потрапив до іншого рахунку. Оновіть дані й перевірте результат."));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Рахунок, клієнта або виріб не знайдено"));}
}
