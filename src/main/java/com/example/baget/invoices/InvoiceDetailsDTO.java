package com.example.baget.invoices;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InvoiceDetailsDTO(
    Long id,
    Long number,
    BigDecimal total,
    BigDecimal paid,
    BigDecimal debt,
    OffsetDateTime createdAt
) {}
