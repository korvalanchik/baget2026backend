package com.example.baget.buh;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface TransactionHistoryView {
    Long getTransactionId();
    OffsetDateTime getTransactionDate();
    TransactionType getTransactionType();
    BigDecimal getAmount();
    String getReference();
    String getNote();
}

