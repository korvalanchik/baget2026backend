package com.example.baget.invoices;

import com.example.baget.customer.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер рахунку */
    @Column(name = "invoice_no", nullable = false, unique = true, length = 50)
    private Long invoiceNo;

    /** Клієнт */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Платник */
    @ManyToOne
    @JoinColumn(name = "payer_id")
    private Customer payer;

    /** Тип рахунку */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceEnums.InvoiceType type;

    /** Статус рахунку */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceEnums.InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false)
    private InvoiceEnums.InvoiceLifecycle lifecycle;

    /** Загальна сума рахунку */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    /** Дата виставлення */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Рядки рахунку (зв’язок із замовленнями) */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceOrder> invoiceOrders = new ArrayList<>();

    /** Додатковий коментар */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Version
    private Long version;

    public Customer getEffectivePayer() {
        return payer != null ? payer : customer;
    }

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null) {
            status = InvoiceEnums.InvoiceStatus.ISSUED;
        }
        if (lifecycle == null) {
            lifecycle = InvoiceEnums.InvoiceLifecycle.ACTIVE;
        }
    }

}
