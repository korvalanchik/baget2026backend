package com.example.baget.telegram;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/telegram")
public class TelegramResource {
 @PostMapping("/send")
 public ResponseEntity<String> sendMessage(@RequestBody TelegramMessageRequest request){
  return ResponseEntity.status(HttpStatus.GONE).body("Копія створюється автоматично під час збереження нового замовлення. Перевірте налаштування Telegram.");
 }
}
