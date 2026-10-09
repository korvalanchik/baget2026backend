package com.example.baget.cash;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/cash")
public class CashController {
 private final CashService service;public CashController(CashService service){this.service=service;}
 @GetMapping public Object overview(Authentication a){return service.overview(a);}
 @GetMapping("/options") public Object options(Authentication a){return service.options(a);}
 @GetMapping("/payment-options") public Object paymentOptions(@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="invoice",required=false) Long invoice,Authentication a){if(branch!=null)return service.paymentOptions(branch,a);if(invoice!=null)return service.invoicePaymentOptions(invoice,a);throw CashService.bad("Оберіть філію або рахунок");}
 @GetMapping("/accounts/{id}/journal") public Object journal(@PathVariable("id") long id,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.journal(id,page,a);}
 @PostMapping("/accounts") public Object create(@RequestBody @Valid CashService.Create r,Authentication a){return service.create(r,a);}
 @PostMapping("/opening") public Object opening(@RequestBody @Valid CashService.Opening r,Authentication a){return service.opening(r,a);}
 @PostMapping("/transfers") public Object send(@RequestBody @Valid CashService.Send r,Authentication a){return service.send(r,a);}
 @PostMapping("/receive") public Object receive(@RequestBody @Valid CashService.Receive r,Authentication a){return service.receive(r,a);}
 @GetMapping("/accounts/{id}/day") public Object day(@PathVariable("id") long id,@RequestParam(name="day",required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate day,Authentication a){return service.reconciliationDay(id,day,a);}
 @GetMapping("/accounts/{id}/reconciliations") public Object history(@PathVariable("id") long id,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.reconciliationHistory(id,page,a);}
 @GetMapping("/reconciliation-differences") public Object differences(Authentication a){return service.reconciliationDifferences(a);}
 @PostMapping("/reconciliations") public Object reconcile(@RequestBody @Valid CashService.Reconcile r,Authentication a){return service.reconcile(r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
