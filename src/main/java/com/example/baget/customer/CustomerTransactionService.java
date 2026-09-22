package com.example.baget.customer;

import com.example.baget.orders.Orders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerTransactionService {

    private final CustomerTransactionRepository customerTransactionRepository;

    public BigDecimal getCustomerBalance(Long customerId) {
        return customerTransactionRepository.sumActiveAmountByCustomer(customerId)
                .orElse(BigDecimal.ZERO);
    }

    public List<CustomerTransaction> getTransactions(Long customerId) {
        return customerTransactionRepository.findByCustomerIdAndActiveTrueOrderByCreatedAtDesc(customerId);
    }

    public CustomerTransaction createInvoice(
            Customer customer,
            Orders order,
            BigDecimal amount,
            String reference
    ) {
        return customerTransactionRepository.save(
                CustomerTransaction.builder()
                        .customer(customer)
                        .order(order)
                        .type(CustomerTransactionType.INVOICE)
                        .amount(amount) // +
                        .createdAt(OffsetDateTime.now())
                        .reference(reference)
                        .build()
        );
    }
}
