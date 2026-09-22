package com.example.baget.customer;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping(value = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerResource {

    private final CustomerService customerService;

    public CustomerResource(final CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public Page<CustomerDTO> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,   // Номер сторінки, за замовчуванням 0
            @RequestParam(defaultValue = "10") int size   // Розмір сторінки, за замовчуванням 10
    ) {
        Pageable pageable = PageRequest.of(page, size);  // Створення об'єкта Pageable для пагінації
        return customerService.getCustomers(pageable);  // Повертаємо сторінку клієнтів
    }

    @GetMapping("/{custNo}")
    public ResponseEntity<CustomerDTO> getCustomer(
            @PathVariable(name = "custNo") final Long custNo) {
        return ResponseEntity.ok(customerService.get(custNo));
    }

    @GetMapping("/searchByPhonePrefix")
    public ResponseEntity<List<CustomerDTO>> searchByPhonePrefix(@RequestParam String prefix) {
        List<CustomerDTO> customers = customerService.findByPhonePrefix(prefix);
        return ResponseEntity.ok(customers);
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Long> createCustomer(@RequestBody @Valid final CustomerDTO customerDTO) {
        final Long createdCustNo = customerService.create(customerDTO);
        return new ResponseEntity<>(createdCustNo, HttpStatus.CREATED);
    }

    @PutMapping("/{custNo}")
    public ResponseEntity<Long> updateCustomer(@PathVariable(name = "custNo") final Long custNo,
            @RequestBody @Valid final CustomerDTO customerDTO) {
        customerService.update(custNo, customerDTO);
        return ResponseEntity.ok(custNo);
    }

    @DeleteMapping("/{custNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteCustomer(@PathVariable(name = "custNo") final Long custNo) {
        customerService.delete(custNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/select")
    public List<CustomerSelectDTO> getCustomersForInvoiceSelect() {
        return customerService.getCustomersForInvoice();
    }

    @GetMapping("/dashboard")
    public CustomerDashboardDTO.Response getDashboard(
            @RequestParam(required = false) Long branchNo,
            @RequestParam(required = false, defaultValue = "false") boolean debtOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        return customerService.getDashboard(branchNo, debtOnly, date, authentication);
    }
}
