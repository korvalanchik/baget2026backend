package com.example.baget.customer;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
@Builder
public record CustomerBalanceDTO (
    Long custNo,
    String company,
    String phone,
    Long invoiceCount,
    BigDecimal balance,
    OffsetDateTime lastPaymentDate
) {}
