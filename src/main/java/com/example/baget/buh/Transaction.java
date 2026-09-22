package com.example.baget.buh;

import com.example.baget.customer.Customer;
import com.example.baget.orders.Orders;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_no")
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private TransactionType transactionType;

    @Column(name = "transaction_date")
    @Builder.Default
    private OffsetDateTime transactionDate = OffsetDateTime.now();

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 50)
    private String reference;

    @Column(length = 20)
    private String status; // Pending, Completed, Canceled

    @Column(columnDefinition = "TEXT")
    private String note;
}
