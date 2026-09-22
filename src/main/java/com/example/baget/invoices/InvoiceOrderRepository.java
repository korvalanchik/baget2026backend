package com.example.baget.invoices;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceOrderRepository extends JpaRepository<InvoiceOrder, Long> {
//    boolean existsByOrder_OrderNoIn(List<Long> orderNos);

    List<InvoiceOrder> findByInvoice_Id(Long id);

//    boolean existsByOrder_OrderNo(Long orderNo);

    boolean existsByOrder_OrderNoAndInvoice_Lifecycle(Long orderNo, InvoiceEnums.InvoiceLifecycle lifecycle);

    @Query("""
        SELECT io
        FROM InvoiceOrder io
        WHERE io.invoice.id IN :invoiceIds
    """)
    List<InvoiceOrder> findByInvoice_IdIn(@Param("invoiceIds") List<Long> invoiceIds);

}
