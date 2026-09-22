package com.example.baget.ledger;

import com.example.baget.customer.CustomerLedgerDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    @SuppressWarnings("JpaQlInspection")
    @Query("""
    SELECT new com.example.baget.customer.CustomerLedgerDTO(
        le.createdAt,
        le.direction,
        le.amount,
        le.category,
        le.note
    )
    FROM LedgerEntry le
    WHERE le.customerId = :customerId
    ORDER BY le.createdAt DESC
    """)
    List<CustomerLedgerDTO> findCustomerLedger(Long customerId);

    @Query("""
        SELECT
            COALESCE(SUM(
                CASE
                    WHEN le.direction = com.example.baget.ledger.LedgerDirection.OUT
                    THEN le.amount
                    ELSE -le.amount
                END
            ), 0)
        FROM LedgerEntry le
        WHERE le.customerId = :customerId
    """)
    BigDecimal getCustomerBalance(Long customerId);

    @Query("""
        SELECT
            COALESCE(SUM(
                CASE
                    WHEN le.direction = com.example.baget.ledger.LedgerDirection.OUT
                    THEN le.amount
                    ELSE -le.amount
                END
            ), 0)
        FROM LedgerEntry le
        WHERE le.invoiceId = :invoiceId
    """)
    BigDecimal calculateInvoiceDebt(@Param("invoiceId") Long invoiceId);

    @Query("""
        SELECT
            COALESCE(SUM(
                CASE
                    WHEN le.direction = com.example.baget.ledger.LedgerDirection.OUT
                    THEN le.amount
                    ELSE -le.amount
                END
            ), 0)
        FROM LedgerEntry le
        LEFT JOIN Invoice i ON i.id = le.invoiceId
        WHERE le.orderId = :orderId
          AND (i IS NULL OR i.lifecycle = com.example.baget.invoices.InvoiceEnums.InvoiceLifecycle.ACTIVE)
        """)
    BigDecimal calculateOrderDebt(@Param("orderId") Long orderId);

    @Query("""
        SELECT le.orderId,
               COALESCE(SUM(CASE WHEN le.direction = com.example.baget.ledger.LedgerDirection.IN THEN le.amount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN le.direction = com.example.baget.ledger.LedgerDirection.OUT THEN le.amount ELSE 0 END), 0)
        FROM LedgerEntry le
        WHERE le.orderId IN :orderIds
        GROUP BY le.orderId
    """)
    List<Object[]> sumInOutByOrders(@Param("orderIds") List<Long> orderIds);

    Optional<LedgerEntry> findTopByInvoiceIdAndDirectionAndCategoryInOrderByCreatedAtDescIdDesc(Long invoiceId, LedgerDirection ledgerDirection, List<LedgerCategory> invoiceOwnershipCategories);
}
