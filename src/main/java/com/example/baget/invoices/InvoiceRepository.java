package com.example.baget.invoices;

import com.example.baget.customer.CustomerInvoiceDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByInvoiceNo(Long invoiceNo);

    @SuppressWarnings("JpaQlInspection")
    @Query("""
    select new com.example.baget.customer.CustomerInvoiceDTO(
             i.id,
             i.invoiceNo,
             i.totalAmount,
             coalesce((
                 select sum(-ct.amount)
                 from CustomerTransaction ct
                 where ct.invoice = i
                   and ct.active = true
                   and ct.amount < 0
             ), 0),
             i.totalAmount - coalesce((
                 select sum(-ct.amount)
                 from CustomerTransaction ct
                 where ct.invoice = i
                   and ct.active = true
                   and ct.amount < 0
             ), 0),
             i.createdAt
    )
    from Invoice i
    where i.customer.custNo = :custNo
    order by i.createdAt desc
    """)
    List<CustomerInvoiceDTO> findInvoicesByCustomer(@Param("custNo") Long custNo);

    @SuppressWarnings("JpaQlInspection")
    @Query("""
    select new com.example.baget.invoices.InvoiceDetailsDTO(
        i.id,
        i.invoiceNo,
        i.totalAmount,
        coalesce(sum(case when ct.amount < 0 then -ct.amount else 0 end), 0),
        i.totalAmount - coalesce(sum(case when ct.amount < 0 then -ct.amount else 0 end), 0),
        i.createdAt
    )
    from Invoice i
    left join CustomerTransaction ct
        on ct.invoice = i
        and ct.active = true
    where i.id = :invoiceId
    group by i.id, i.invoiceNo, i.totalAmount, i.createdAt
    """)
    Optional<InvoiceDetailsDTO> findInvoiceDetails(@Param("invoiceId") Long invoiceId);

    Optional<Invoice> findByInvoiceNoAndLifecycle(Long invoiceNo, InvoiceEnums.InvoiceLifecycle lifecycle);

    @Query("""
        SELECT COALESCE(SUM(
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
        select i
        from Invoice i
        where i.customer.custNo = :customerId
          and i.lifecycle = com.example.baget.invoices.InvoiceEnums.InvoiceLifecycle.ACTIVE
        order by i.createdAt desc
    """)
    List<Invoice> findActiveInvoices(@Param("customerId") Long customerId);

}