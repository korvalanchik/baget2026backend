package com.example.baget.my_company;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyDetailsService {

    private final CompanyDetailsRepository repository;

    public CompanyDetails get() {
        return repository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Company details not found"));
    }
}
