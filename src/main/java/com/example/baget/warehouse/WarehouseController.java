package com.example.baget.warehouse;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.qrcoding.QrGenerator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/warehouse")
public class WarehouseController {
    private final WarehouseService service;
    private final QrGenerator qr;
    @Value("${frontend.product-tracking-url:${frontend.url}}") private String frontend;
    public WarehouseController(WarehouseService service,QrGenerator qr){this.service=service;this.qr=qr;}
    @GetMapping("/products") public Object queue(@RequestParam(defaultValue="0",name="page") int page,Authentication a){return service.queue(page,a);}
    @GetMapping("/products/by-id/{id}") public Object byId(@PathVariable("id") long id,Authentication a){return service.tracking(service.trackingForProduct(id,a),a);}
    @GetMapping("/products/{token}") public Object product(@PathVariable("token") String token,Authentication a){return service.tracking(token,a);}
    @GetMapping("/products/{token}/label") public Object label(@PathVariable("token") String token,Authentication a) throws Exception {
        var p=service.tracking(token,a);
        if(!"COMPLETED".equals(p.get("state"))) throw new PreviewException(org.springframework.http.HttpStatus.CONFLICT,"NOT_COMPLETED","Етикетка доступна після завершення виробу");
        String url=frontend.replaceAll("/+$","")+"/?product="+token;
        return Map.of("url",url,"image","data:image/png;base64,"+Base64.getEncoder().encodeToString(qr.generateQrPng(url,320,320)));
    }
    @PostMapping("/products/{token}/send") public Object send(@PathVariable("token") String token,@Valid @RequestBody WarehouseService.Transfer r,Authentication a){return service.transfer(token,r,false,a);}
    @PostMapping("/products/{token}/accept") public Object accept(@PathVariable("token") String token,@Valid @RequestBody WarehouseService.Transfer r,Authentication a){return service.transfer(token,r,true,a);}
    @PostMapping("/products/{token}/cancel") public Object cancel(@PathVariable("token") String token,@Valid @RequestBody WarehouseService.Transfer r,Authentication a){return service.cancelTransfer(token,r,a);}
    @GetMapping("/materials") public Object materials(@RequestParam(defaultValue="",name="q") String q,@RequestParam(defaultValue="0",name="page") int page,@RequestParam(defaultValue="false",name="shortageOnly") boolean shortage,Authentication a){return service.materials(q,page,shortage,a);}
    @PostMapping("/materials") public Object createMaterial(@Valid @RequestBody WarehouseService.NewMaterial r,Authentication a){return service.createMaterial(r,a);}
    @GetMapping("/materials/{id}") public Object material(@PathVariable("id") long id,Authentication a){return service.material(id,a);}
    @PostMapping("/materials/{id}/stock") public Object stock(@PathVariable("id") long id,@Valid @RequestBody WarehouseService.Stock r,Authentication a){return service.stock(id,r,a);}
    @PostMapping("/materials/{id}/requests") public Object request(@PathVariable("id") long id,@Valid @RequestBody WarehouseService.Purchase r,Authentication a){return service.request(id,r,a);}
    @PostMapping("/materials/{id}/requests/{rid}") public Object state(@PathVariable("id") long id,@PathVariable("rid") long rid,@Valid @RequestBody WarehouseService.RequestState r,Authentication a){return service.requestState(id,rid,r,a);}
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Ключ операції вже використано. Оновіть дані й перевірте історію."));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Матеріал не знайдено або він не налаштований як матеріал складу"));}
}
