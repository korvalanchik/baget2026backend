package com.example.baget.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerMapper {
    CustomerDTO mapToDTO(final Customer customer) {
        CustomerDTO customerDTO = new CustomerDTO();
        customerDTO.setCustNo(customer.getCustNo());
        customerDTO.setCompany(customer.getCompany());
        customerDTO.setAddr1(customer.getAddr1());
        customerDTO.setComment(customer.getComment());
        customerDTO.setCity(customer.getCity());
        customerDTO.setState(customer.getState());
        customerDTO.setZip(customer.getZip());
        customerDTO.setCountry(customer.getCountry());
        customerDTO.setPhone(customer.getPhone());
        customerDTO.setMobile(customer.getMobile());
        customerDTO.setTaxRate(customer.getTaxRate());
        customerDTO.setContact(customer.getContact());
        customerDTO.setLastInvoiceDate(customer.getLastInvoiceDate());
        customerDTO.setPriceLevel(customer.getPriceLevel());
        return customerDTO;
    }

    void mapToEntity(final CustomerDTO customerDTO, final Customer customer) {
        customer.setCompany(customerDTO.getCompany());
        customer.setAddr1(customerDTO.getAddr1());
        customer.setComment(customerDTO.getComment());
        customer.setCity(customerDTO.getCity());
        customer.setState(customerDTO.getState());
        customer.setZip(customerDTO.getZip());
        customer.setCountry(customerDTO.getCountry());
        customer.setPhone(customerDTO.getPhone());
        customer.setMobile(customerDTO.getMobile());
        customer.setTaxRate(customerDTO.getTaxRate());
        customer.setContact(customerDTO.getContact());
        customer.setLastInvoiceDate(customerDTO.getLastInvoiceDate());
        customer.setPriceLevel(customerDTO.getPriceLevel());
    }

}
