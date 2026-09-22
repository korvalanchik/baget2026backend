package com.example.baget.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CustomerBalanceProjection {

    Long getCustNo();
    String getCompany();
    String getMobile();

    BigDecimal getBalance();
    Long getInvoiceCount();
    LocalDateTime getLastPaymentDate();

    Long getPendingOrders();
}