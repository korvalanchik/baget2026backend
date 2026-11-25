package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/calculated-orders")
public class CalculatedOrderController {
    private final CalculatedOrderService service;
    private final PreliminaryOrderInvoicePdf preliminaryInvoices;
    public CalculatedOrderController(CalculatedOrderService service, PreliminaryOrderInvoicePdf preliminaryInvoices){this.service=service;this.preliminaryInvoices=preliminaryInvoices;}
    @PostMapping public CalculatedOrderService.View create(@Valid @RequestBody CalculatedOrderRequest r,Authentication a){return service.create(r,a);}
    @GetMapping("/{id}") public CalculatedOrderService.View read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
    @GetMapping(value="/{id}/preliminary-invoice.pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preliminaryInvoice(@PathVariable("id") long id, Authentication a) throws Exception {
        var order=service.read(id,a);
        if (!"RESELLER".equals(order.commerceMode())) throw new PreviewException(HttpStatus.NOT_FOUND,"PRELIMINARY_INVOICE_NOT_FOUND","Попередній рахунок доступний лише для філій-посередників");
        byte[] pdf=preliminaryInvoices.get(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=preliminary-order-"+id+".pdf").contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
    @PutMapping("/{id}") public CalculatedOrderService.View update(@PathVariable("id") long id,@Valid @RequestBody CalculatedOrderRequest r,Authentication a){return service.update(id,r,a);}
    public record Revision(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long expectedRevision) {}
    @PostMapping("/{id}/cancel") public CalculatedOrderService.View cancel(@PathVariable("id") long id,@Valid @RequestBody Revision r,Authentication a){return service.cancel(id,r.expectedRevision(),a);}
    @PostMapping("/{id}/reserve") public CalculatedOrderService.View reserve(@PathVariable("id") long id,Authentication a){return service.refill(id,a);}
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> failure(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
