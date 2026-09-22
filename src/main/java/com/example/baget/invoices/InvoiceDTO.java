package com.example.baget.invoices;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceDTO {

    private Long id;                  // PK інвойсу
    private Long invoiceNo;         // номер інвойсу
    private Long customerId;          // ID клієнта
    private String customerName;      // опційно: ім'я клієнта
    private String type;              // SIMPLE / CONSOLIDATED
    private String status;            // ISSUED / PAID / CANCELED
    private BigDecimal totalAmount;   // загальна сума
    private OffsetDateTime createdAt; // дата створення
    private String note;              // нотатки / reference

    private List<InvoiceOrderDTO> orders; // список замовлень
}
