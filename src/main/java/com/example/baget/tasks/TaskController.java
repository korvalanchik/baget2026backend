package com.example.baget.tasks;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
 private final TaskService service;
 public TaskController(TaskService service){this.service=service;}
 @GetMapping public Object overview(Authentication a){return service.overview(a);}
 @GetMapping("/{key}") public Object queue(@PathVariable("key") String key,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.queue(key,page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
