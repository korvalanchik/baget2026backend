package com.example.baget.parts;

import com.example.baget.orders.Orders;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== Зв’язки =====

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_no", nullable = false)
    private Parts part;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_no")
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_type_id", nullable = false)
    private InventoryTransactionType transactionType;

    // ===== Дані =====

    /**
     * + quantity  → прихід
     * - quantity  → списання
     */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /**
     * Собівартість одиниці (вже з доставкою і % відходів)
     */
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    /**
     * quantity * unit_cost
     */
    @Column(name = "total_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "transaction_date", nullable = false)
    private OffsetDateTime transactionDate = OffsetDateTime.now();

    private String reference;
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // ===== Автоматичний перерахунок =====
    @PrePersist
    @PreUpdate
    private void recalcTotalCost() {
        if (quantity != null && unitCost != null) {
            this.totalCost = unitCost.multiply(quantity);
        }
    }
}
