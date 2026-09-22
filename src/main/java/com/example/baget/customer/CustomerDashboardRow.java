package com.example.baget.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CustomerDashboardRow {

    Long getCustomerId();
    String getCompany();
    String getMobile();

    Integer getPendingOrders();
    Integer getInvoiceCount();

    BigDecimal getBalance();
    LocalDateTime getLastPaymentDate();

    Integer getConsolidatedInvoices();
    BigDecimal getTotalTurnover();
}