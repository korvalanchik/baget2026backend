package com.example.baget.buh;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, Long> {
    List<TransactionTypeProjection> findAllProjectedBy();
    Optional<TransactionType>  findByCode(String code);
}