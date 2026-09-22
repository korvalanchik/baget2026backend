package com.example.baget.buh;

import lombok.Builder;

@Builder
public record TransactionResultDTO(
        boolean success,
        String message,
        TransactionDTO transaction

) {}
