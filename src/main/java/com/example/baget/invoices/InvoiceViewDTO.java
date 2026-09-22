package com.example.baget.invoices;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class InvoiceViewDTO {

    private Long invoiceId;
    private Long invoiceNo;

    private String customerName;

    private InvoiceEnums.InvoiceType type;
    private InvoiceEnums.InvoiceStatus status;

    private BigDecimal totalAmount;

    private OffsetDateTime createdAt;

    private List<InvoiceOrderViewDTO> orders;
}