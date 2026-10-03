package com.example.baget.reconciliation;
import com.example.baget.ledger.LedgerCategory;
import com.example.baget.ledger.LedgerDirection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ReconciliationEntry(Long id, OffsetDateTime createdAt, Long branchNo, String branchName,
 LedgerDirection direction, LedgerCategory category, BigDecimal amount, Long invoiceId, String reference, String note) {}
