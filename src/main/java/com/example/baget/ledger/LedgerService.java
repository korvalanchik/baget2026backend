package com.example.baget.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerRepository ledgerRepository;

    public void createEntry(LedgerRequest request) {

        ledgerRepository.save(
                LedgerEntry.builder()
                        .branch(request.branch())
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