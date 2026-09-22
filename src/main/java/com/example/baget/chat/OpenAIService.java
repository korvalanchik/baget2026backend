package com.example.baget.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class OpenAIService {

    private final WebClient webClient;

    public OpenAIService(@Value("${openai.api.key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1/responses")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<String> ask(String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-3.5-turbo", // або gpt-4.1 / gpt-4o
                "input", userMessage
        );

        return webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class);
    }
}
