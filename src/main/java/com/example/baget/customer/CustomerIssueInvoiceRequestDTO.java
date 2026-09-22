package com.example.baget.customer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIssueInvoiceRequestDTO {
    private List<Long> orderNos;      // список замовлень для інвойсу
    private Long invoiceCustomerId;   // клієнт, який за все це буде платити
    private BigDecimal amount;        // сума, яку клієнт хоче сплатити
    private String reference;         // optional note / посилання
    private OffsetDateTime shipDate;  // optional дата відвантаження
}
