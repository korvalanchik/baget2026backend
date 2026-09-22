package com.example.baget.customer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerPaymentDTO(
        Long id,
        OffsetDateTime date,
        String type,
        BigDecimal amount,
        String comment
) {}
