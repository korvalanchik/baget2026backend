package com.example.baget.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerTransactionRepository extends JpaRepository<CustomerTransaction, Long> {

    @Query("SELECT SUM(ct.amount) FROM CustomerTransaction ct WHERE ct.customer.custNo = :customerId AND ct.active = true")
    Optional<BigDecimal> sumActiveAmountByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT ct FROM CustomerTransaction ct WHERE ct.customer.custNo = :customerId AND ct.active = true ORDER BY ct.createdAt DESC")
    List<CustomerTransaction> findByCustomerIdAndActiveTrueOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    @SuppressWarnings("JpaQlInspection")
    @Query("""
    select new com.example.baget.customer.CustomerPaymentDTO(
        ct.id,
        ct.createdAt,
        cast(ct.type as string),
        abs(ct.amount),
        ct.note
    )
    from CustomerTransaction ct
    where ct.invoice.id = :invoiceId
      and ct.type = com.example.baget.customer.CustomerTransactionType.PAYMENT
      and ct.active = true
    order by ct.createdAt asc
    """)
    List<CustomerPaymentDTO> findPaymentsByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
    select
        coalesce(sum(ct.amount), 0)
    from CustomerTransaction ct
    where
        ct.type in (com.example.baget.customer.CustomerTransactionType.ADVANCE,
                    com.example.baget.customer.CustomerTransactionType.ADVANCE_ALLOCATION)
        and ct.customer.custNo = :customerId
        and ct.active = true
    """)
    BigDecimal calculateAvailableAdvance(@Param("customerId") Long customerId);

    @Query("""
    select coalesce(sum(ct.amount),0)
    from CustomerTransaction ct
    where ct.invoice.id = :invoiceId
      and ct.type = com.example.baget.customer.CustomerTransactionType.PAYMENT
      and ct.active = true
    """)
    BigDecimal totalPayments(@Param("invoiceId") Long invoiceId);

    @Query("""
    select abs(coalesce(sum(ct.amount),0))
    from CustomerTransaction ct
    where ct.invoice.id = :invoiceId
      and ct.type = com.example.baget.customer.CustomerTransactionType.ADVANCE_ALLOCATION
      and ct.active = true
    """)
    BigDecimal totalAdvanceAllocated(@Param("invoiceId") Long invoiceId);
}