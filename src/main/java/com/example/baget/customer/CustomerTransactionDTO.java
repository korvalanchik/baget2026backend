package com.example.baget.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class CustomerTransactionDTO {
    private Long id;
    private Long customerId;
    private Long orderNo;
    private Long invoiceId;
    private CustomerTransactionType type;
    private BigDecimal amount;
    private Long parentTransactionId;
    private OffsetDateTime createdAt;
    private String reference;
    private String note;
}
