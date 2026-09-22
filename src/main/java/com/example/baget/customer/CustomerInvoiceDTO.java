package com.example.baget.customer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

//@Builder
public record CustomerInvoiceDTO(
        Long id,
        Long invoiceNo,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal debt,
        OffsetDateTime createdAt
) {}
