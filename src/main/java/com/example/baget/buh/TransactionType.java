package com.example.baget.buh;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transaction_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    @Column(nullable = false, unique = true, length = 20)
    private String code; // INVOICE, PAYMENT, REFUND

    @Column(length = 100)
    private String description;
}
