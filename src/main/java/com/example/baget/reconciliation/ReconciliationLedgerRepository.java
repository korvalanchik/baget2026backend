package com.example.baget.reconciliation;
import com.example.baget.ledger.LedgerEntry;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
public interface ReconciliationLedgerRepository extends Repository<LedgerEntry,Long> {
 @Query("SELECT COALESCE(SUM(CASE WHEN le.direction=com.example.baget.ledger.LedgerDirection.OUT THEN le.amount ELSE -le.amount END),0) FROM LedgerEntry le WHERE le.customerId=:customer AND le.createdAt<:before")
 BigDecimal balance(@Param("customer") long customer,@Param("before") OffsetDateTime before);
 @Query("SELECT COALESCE(SUM(CASE WHEN le.direction=com.example.baget.ledger.LedgerDirection.IN THEN le.amount ELSE -le.amount END),0) FROM LedgerEntry le WHERE le.customerId=:customer AND le.invoiceId IS NULL AND le.createdAt<:before")
 BigDecimal unallocated(@Param("customer") long customer,@Param("before") OffsetDateTime before);
 @Query("SELECT new com.example.baget.reconciliation.ReconciliationEntry(le.id,le.createdAt,le.branch.branchNo,le.branch.name,le.direction,le.category,le.amount,le.invoiceId,le.reference,le.note) FROM LedgerEntry le WHERE le.customerId=:customer AND le.createdAt>=:from AND le.createdAt<:until ORDER BY le.createdAt,le.id")
 List<ReconciliationEntry> entries(@Param("customer") long customer,@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until,Pageable page);
 @Query("SELECT le.invoiceId, SUM(CASE WHEN le.direction=com.example.baget.ledger.LedgerDirection.OUT THEN le.amount ELSE -le.amount END) FROM LedgerEntry le WHERE le.customerId=:customer AND le.createdAt<:before AND le.invoiceId IN :ids GROUP BY le.invoiceId")
 List<Object[]> invoiceBalances(@Param("customer") long customer,@Param("before") OffsetDateTime before,@Param("ids") List<Long> ids);
}
