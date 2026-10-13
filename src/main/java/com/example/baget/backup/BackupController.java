package com.example.baget.backup;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
@RestController
@RequestMapping("/api/settings/backups")
public class BackupController {
 private final BackupService service;private final BackupCloud cloud;
 public BackupController(BackupService service,BackupCloud cloud){this.service=service;this.cloud=cloud;}
 @GetMapping public Map<String,Object> read(Authentication a){return service.read(a);}
 @PutMapping public Map<String,Object> save(@RequestBody BackupService.Settings r,Authentication a){return service.save(r,a);}
 public record Request(String requestKey){}
 @PostMapping("/run") public Map<String,Object> run(@RequestBody Request r,Authentication a){String id=service.enqueue(r==null?null:r.requestKey(),a);return Map.of("id",id,"invoked",cloud.launch());}
 @GetMapping("/{id}/download") public void download(@PathVariable("id") String id,Authentication a,HttpServletResponse response) throws IOException {
  var f=service.file(id,a);String name=Objects.toString(f.get("file_name"),"baget.sql.gz");
  try(var in=cloud.download((String)f.get("file_id"))) {
   response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
   response.setHeader(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString());
   response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
   in.transferTo(response.getOutputStream());
  }
 }
}
