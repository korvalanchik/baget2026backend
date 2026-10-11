package com.example.baget.telegram;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController
@RequestMapping("/api/settings/telegram")
public class TelegramCopyController {
 private final TelegramCopySettings settings;
 public TelegramCopyController(TelegramCopySettings settings){this.settings=settings;}
 @GetMapping public Object read(Authentication a){return settings.read(a);}
 @PutMapping public Object save(@RequestBody TelegramCopySettings.Update r,Authentication a){return settings.save(r,a);}
 @PostMapping("/deliveries/{id}/retry") public void retry(@PathVariable long id,Authentication a){settings.retry(id,a);}
}
