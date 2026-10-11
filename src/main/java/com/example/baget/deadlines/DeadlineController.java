package com.example.baget.deadlines;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class DeadlineController {
 private final LeadTimeService settings;private final DeadlineService deadlines;
 public DeadlineController(LeadTimeService settings,DeadlineService deadlines){this.settings=settings;this.deadlines=deadlines;}
 @GetMapping("/api/settings/lead-time") public Object settings(Authentication a){return settings.read(a);}
 @PutMapping("/api/settings/lead-time") public Object save(@RequestBody LeadTimeService.Settings r,Authentication a){return settings.save(r,a);}
 @GetMapping("/api/tasks/deadlines") public Object tasks(@RequestParam(name="bucket",defaultValue="soon") String bucket,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return deadlines.read(bucket,page,a);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
}
