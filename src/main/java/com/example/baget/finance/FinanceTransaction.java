package com.example.baget.finance;

import com.example.baget.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "finance_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FinanceDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceCategory category;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Необовʼязково: хто провів */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User createdBy;

    /** Опціонально — звʼязок з клієнтським платежем */
    @Column(name = "customer_transaction_id")
    private Long customerTransactionId;

    @Column(length = 50)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String note;
}
