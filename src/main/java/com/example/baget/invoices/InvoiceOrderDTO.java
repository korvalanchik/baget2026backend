package com.example.baget.invoices;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceOrderDTO {

    private Long orderNo;           // номер замовлення
    private BigDecimal amount;      // сума замовлення в рахунку
    private Integer statusOrder;    // статус замовлення (опційно)
    private OffsetDateTime shipDate; // дата відвантаження (опційно)
}
