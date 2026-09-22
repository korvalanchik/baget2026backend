package com.example.baget.parts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_transaction_types")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransactionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;          // INBOUND, OUTBOUND, ADJUSTMENT, WRITE_OFF

    @Column(nullable = false, length = 100)
    private String name;          // Людська назва

    @Column(length = 255)
    private String description;
}