package com.example.baget.customer;

import com.example.baget.ledger.LedgerCategory;
import com.example.baget.ledger.LedgerDirection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerLedgerDTO(

        OffsetDateTime date,
        LedgerDirection direction,
        BigDecimal amount,
        LedgerCategory sourceType,
        String comment

) {

    public BigDecimal in() {
        return direction == LedgerDirection.IN ? amount : null;
    }

    public BigDecimal out() {
        return direction == LedgerDirection.OUT ? amount : null;
    }

}
