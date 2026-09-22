package com.example.baget.telegram;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telegram")
public class TelegramResource {
    private final TelegramService telegramService;

    public TelegramResource(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

//    @PostMapping("/send")
//    public ResponseEntity<String> sendMessage(@RequestBody String message) {
//        try {
//            telegramService.sendMessage(message);
//            return ResponseEntity.ok("Повідомлення відправлено у майстерню!");
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("Помилка відправки замовлення: " + e.getMessage());
//        }
//    }


    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody TelegramMessageRequest request) {
        try {
            telegramService.sendMessage(request);
            return ResponseEntity.ok("Повідомлення відправлено у майстерню!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Помилка відправки замовлення: " + e.getMessage());
        }
    }


}
