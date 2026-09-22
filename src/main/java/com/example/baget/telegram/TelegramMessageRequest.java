package com.example.baget.telegram;

public record TelegramMessageRequest(
        String message,
        String branchName
) {}
