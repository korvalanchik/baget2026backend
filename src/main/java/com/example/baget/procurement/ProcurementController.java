package com.example.baget.procurement;

import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/warehouse/procurement")
public class ProcurementController {
    private final ProcurementService service;
    public ProcurementController(ProcurementService service){this.service=service;}
    @GetMapping("/needs") public Object needs(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="onlyNeeded",defaultValue="true") boolean needed,Authentication a){return service.needs(q,page,needed,a);}
    @GetMapping("/needs/{part}") public Object need(@PathVariable("part") long part,Authentication a){return service.need(part,a);}
    @PostMapping("/needs/{part}/policy") public Object policy(@PathVariable("part") long part,@Valid @RequestBody ProcurementService.Policy r,Authentication a){return service.policy(part,r,a);}
    @GetMapping("/vendors") public Object vendors(Authentication a){return service.vendors(a);}
    @GetMapping("/orders") public Object list(@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(page,a);}
    @GetMapping("/orders/{id}") public Object read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
    @PostMapping("/orders") public Object create(@Valid @RequestBody ProcurementService.Draft r,Authentication a){return service.create(r,a);}
    @PostMapping("/orders/{id}/edit") public Object edit(@PathVariable("id") long id,@Valid @RequestBody ProcurementService.Edit r,Authentication a){return service.edit(id,r,a);}
    @PostMapping("/orders/{id}/place") public Object place(@PathVariable("id") long id,@Valid @RequestBody ProcurementService.Action r,Authentication a){return service.place(id,r,a);}
    @PostMapping("/orders/{id}/schedule") public Object schedule(@PathVariable("id") long id,@Valid @RequestBody ProcurementService.Schedule r,Authentication a){return service.reschedule(id,r,a);}
    @PostMapping("/orders/{id}/close") public Object close(@PathVariable("id") long id,@Valid @RequestBody ProcurementService.Action r,Authentication a){return service.close(id,r,a);}
    @PostMapping("/orders/{id}/receipts") public Object receive(@PathVariable("id") long id,@Valid @RequestBody ProcurementService.Receipt r,Authentication a){return service.receive(id,r,a);}
    @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Документ або налаштований складський матеріал не знайдено"));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Ключ операції вже використано. Повторіть запит з тим самим ключем або відкрийте документ."));}
}
