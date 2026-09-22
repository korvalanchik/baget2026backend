package com.example.baget.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {
}
