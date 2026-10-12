package com.example.baget.reports;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@RestController
@RequestMapping("/api/reports/management-overview")
public class ManagementOverviewController {
 private final ManagementOverviewService service;
 public ManagementOverviewController(ManagementOverviewService service){this.service=service;}
 @GetMapping("/branches") public Object branches(Authentication a){return service.branches(a);}
 @GetMapping public Object read(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="mode",defaultValue="ALL") String mode,Authentication a){return service.read(from,to,branch,mode,a);}
}
