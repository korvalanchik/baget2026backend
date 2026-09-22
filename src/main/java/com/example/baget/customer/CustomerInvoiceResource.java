package com.example.baget.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerInvoiceResource {
    private final CustomerService customerService;
    @GetMapping("/{custNo}/invoices")
    public List<CustomerInvoiceDTO> getInvoices(@PathVariable Long custNo) {
        return customerService.getInvoicesByCustomer(custNo);
    }

}
