package com.example.baget.ledger;

import com.example.baget.branch.Branch;
import com.example.baget.customer.Customer;
import com.example.baget.users.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LedgerRequest(
        Branch branch,
        LedgerDirection direction,
        LedgerCategory category,
        BigDecimal amount,
        OffsetDateTime createdAt,
        User createdBy,

        Long customerId,
        Long customerTransactionId,
        Customer payer,
        Long invoiceId,

        String reference,
        String note
) {}