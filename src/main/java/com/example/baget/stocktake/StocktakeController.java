package com.example.baget.stocktake;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/warehouse/stocktakes")
public class StocktakeController {
 private final StocktakeService service;
 public StocktakeController(StocktakeService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.list(page,a);}
 @GetMapping("/{id}") public Object read(@PathVariable("id") long id,Authentication a){return service.read(id,a);}
 @PostMapping public Object create(@Valid @RequestBody StocktakeService.Create r,Authentication a){return service.create(r,a);}
 @PostMapping("/{id}/save") public Object save(@PathVariable("id") long id,@Valid @RequestBody StocktakeService.Save r,Authentication a){return service.save(id,r,a);}
 @PostMapping("/{id}/review") public Object review(@PathVariable("id") long id,@Valid @RequestBody StocktakeService.Revision r,Authentication a){return service.review(id,r,a);}
 @PostMapping("/{id}/recount") public Object recount(@PathVariable("id") long id,@Valid @RequestBody StocktakeService.Recount r,Authentication a){return service.recount(id,r,a);}
 @PostMapping("/{id}/cancel") public Object cancel(@PathVariable("id") long id,@Valid @RequestBody StocktakeService.Cancel r,Authentication a){return service.cancel(id,r,a);}
 @PostMapping("/{id}/post") public Object post(@PathVariable("id") long id,@Valid @RequestBody StocktakeService.Post r,Authentication a){return service.post(id,r,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Акт або матеріал не знайдено"));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","Ключ операції вже використаний. Оновіть документ або повторіть запит з тим самим ключем."));}
}
