package com.example.baget.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByName(String name);

    Optional<Branch> findByBranchNo(Long aLong);

    List<Branch> findAllByOrderByBranchNoAsc();

    List<Branch> findAllByActiveTrueOrderByBranchNoAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndBranchNoNot(String name, Long branchNo);
}
