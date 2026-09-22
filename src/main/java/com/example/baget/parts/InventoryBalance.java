package com.example.baget.parts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_balance")
@Getter
@Setter
@NoArgsConstructor
public class InventoryBalance {

    @Id
    @Column(name = "part_no")
    private Long partNo;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "part_no")
    private Parts part;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "avg_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal avgCost;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
