package com.example.baget.buh;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionTypeDTO {
    private Long typeId;

    private String code; // INVOICE, PAYMENT, REFUND

    private String description;

}
