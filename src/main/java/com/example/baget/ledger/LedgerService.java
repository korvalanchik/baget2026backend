package com.example.baget.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerRepository ledgerRepository;

    public void createEntry(LedgerRequest request) { createEntry(request,null); }

    public void createEntry(LedgerRequest request,Long cashAccountId) {

        ledgerRepository.save(
                LedgerEntry.builder()
                        .branch(request.branch())
                        .cashAccountId(cashAccountId)
                        .direction(request.direction())
                        .category(request.category())
                        .amount(request.amount())
                        .createdAt(request.createdAt())
                        .createdBy(request.createdBy())

                        .customerId(request.customerId())
                        .customerTransactionId(request.customerTransactionId())
                        .payer(request.payer())
                        .invoiceId(request.invoiceId())
                        .reference(request.reference())
                        .note(request.note())
                        .build()
        );
    }
}