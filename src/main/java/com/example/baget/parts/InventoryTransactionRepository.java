package com.example.baget.parts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("select coalesce(sum(abs(it.totalCost)), 0) from InventoryTransaction it " +
        "join it.transactionType tt " +
        "where tt.code = 'OUTBOUND' and it.order.orderNo = :orderNo")
    BigDecimal getOrderCogs(Long orderNo);
}
