package com.example.baget.orders;

import java.math.BigDecimal;

public record OrderPaySummaryDTO(
        Long orderNo,
        BigDecimal billed,
        BigDecimal paid,
        BigDecimal due
) {}
