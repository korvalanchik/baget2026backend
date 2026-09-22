package com.example.baget.telegram;

import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TelegramService {

    @Value("${bot.token}")
    private String botToken;

    @Value("${CHAT_ID}")
    private String adminChatId;

    private final UsersRepository userRepository;
    private final RestTemplate restTemplate;

    public TelegramService(UsersRepository userRepository, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    public void sendMessage(TelegramMessageRequest request) {
        String text = request.message();
        String branchName = request.branchName();

        Set<Long> sent = new HashSet<>();

        try {
            // 1. Адмін
            safeSend(text, Long.valueOf(adminChatId), sent);

            // 2. Користувач
            sendToCurrentUser(text, sent);

            // 3. Бухгалтери
            if (branchName != null && !branchName.isEmpty()) {
                sendToAccountants(text, branchName, sent);
            }

        } catch (Exception e) {
            log.error("Error in TelegramService.sendMessage", e);
        }
    }

//    Set<Long> sentChatIds = new HashSet<>();

    private void safeSend(String text, Long chatId, Set<Long> sent) {
        if (chatId != null && sent.add(chatId)) {
            sendMessageToChat(text, chatId);
        }
    }

    private void sendToCurrentUser(String text, Set<Long> sent) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            String username = authentication.getName();

            userRepository.findByUsername(username).ifPresent(user -> safeSend(text, user.getTelegramId(), sent));

        }
    }

    private void sendToAccountants(String text, String branchName, Set<Long> sent) {
        List<User> accountants =
                userRepository.findUsersByRoleAndBranch("ROLE_COUNTER", branchName);

        for (User user : accountants) {
            safeSend(text, user.getTelegramId(), sent);
        }
    }

    public void sendMessageToChat(String messageText, Long chatId) {
        try {
            String telegram_API_URL = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            Map<String, Object> requestBody = Map.of(
                    "chat_id", chatId,
                    "text", messageText,
                    "parse_mode", "HTML"
            );

            restTemplate.postForObject(telegram_API_URL, requestBody, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



//    public void sendMessage(String messageJson) {
//        try {
//            // Парсимо JSON
//            ObjectMapper objectMapper = new ObjectMapper();
//            Map<String, Object> messageMap = objectMapper.readValue(messageJson, new TypeReference<>() {});
//            String text = messageMap.getOrDefault("message", "").toString();
//
//            // 1. Адміну
//            sendMessageToChat(text, Long.valueOf(adminChatId));
//
//            // 2. Клієнту (авторизованому користувачу)
//            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//            if (authentication != null && authentication.isAuthenticated()
//                    && !"anonymousUser".equals(authentication.getPrincipal())) {
//
//                String username = authentication.getName();
//
//                User user = userRepository.findByUsername(username).orElse(null);
//
//                if (user != null && user.getTelegramId() != null) {
//                    sendMessageToChat(text, user.getTelegramId());
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
