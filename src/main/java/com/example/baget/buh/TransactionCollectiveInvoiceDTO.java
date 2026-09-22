package com.example.baget.buh;

import com.example.baget.orders.OrderPaySummaryDTO;

import java.math.BigDecimal;
import java.util.List;

public record TransactionCollectiveInvoiceDTO(
        Long invoiceNo,
        List<OrderPaySummaryDTO> orders, // список замовлень у цьому рахунку
        BigDecimal totalBilled,           // нараховано
        BigDecimal totalPaid,             // сплачено
        BigDecimal totalDue,              // залишок
        BigDecimal totalCustomerBalance        // баланс клієнта
) {}
