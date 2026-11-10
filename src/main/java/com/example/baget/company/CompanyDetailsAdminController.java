package com.example.baget.company;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/company-details")
@RequiredArgsConstructor
public class CompanyDetailsAdminController {

    private final CompanyDetailsAdminService service;

    @GetMapping
    public CompanyDetailsAdminDTO get() {
        return service.get();
    }

    @PutMapping
    public CompanyDetailsAdminDTO update(@Valid @RequestBody CompanyDetailsAdminRequest request) {
        return service.update(request);
    }
}
