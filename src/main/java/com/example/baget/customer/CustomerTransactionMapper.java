package com.example.baget.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerTransactionMapper {
    public CustomerTransactionDTO toDTO(CustomerTransaction tx) {
        return CustomerTransactionDTO.builder()
                .id(tx.getId())
                .customerId(tx.getCustomer().getCustNo())
                .invoiceId(tx.getInvoice() != null ? tx.getInvoice().getId() : null)
                .orderNo(tx.getOrder() != null ? tx.getOrder().getOrderNo() : null)
                .amount(tx.getAmount())
                .type(tx.getType())
                .parentTransactionId(tx.getParentTransactionId()) // <-- додаємо тут
                .note(tx.getNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
