package com.example.baget.order_counter;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TempOrderCounterRepository extends JpaRepository<TempOrderCounter, Integer> {
}