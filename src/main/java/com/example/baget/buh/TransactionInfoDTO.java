package com.example.baget.buh;

import java.math.BigDecimal;

public record TransactionInfoDTO(
        Long orderNo,
        Long customerId,
        BigDecimal itemsTotal,
        BigDecimal amountPaid,
        BigDecimal amountDueN,
        BigDecimal customerBalance
) {}