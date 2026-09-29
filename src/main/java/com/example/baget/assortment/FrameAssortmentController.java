package com.example.baget.assortment;
import com.example.baget.calculation.preview.PreviewException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/warehouse/assortment")
public class FrameAssortmentController {
 private final FrameAssortmentService service;
 public FrameAssortmentController(FrameAssortmentService service){this.service=service;}
 @GetMapping public Object matrix(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="activeOnly",defaultValue="false") boolean active,Authentication a){return service.matrix(q,page,active,a);}
 @PostMapping("/{part}/branches/{branch}") public Object sample(@PathVariable("part") long part,@PathVariable("branch") long branch,@Valid @RequestBody FrameAssortmentService.Change r,Authentication a){return service.sample(part,branch,r,a);}
 @PostMapping("/{part}/purchase") public Object purchase(@PathVariable("part") long part,@Valid @RequestBody FrameAssortmentService.Change r,Authentication a){return service.purchase(part,r,a);}
 @GetMapping("/{part}/history") public Object history(@PathVariable("part") long part,Authentication a){return service.history(part,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
