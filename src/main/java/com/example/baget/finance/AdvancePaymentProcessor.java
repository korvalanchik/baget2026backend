package com.example.baget.finance;

import com.example.baget.customer.CustomerTransaction;
import com.example.baget.customer.CustomerTransactionRepository;
import com.example.baget.customer.CustomerTransactionType;
import com.example.baget.invoices.InvoicePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvancePaymentProcessor implements PaymentProcessor {

    private final CustomerTransactionRepository customerTxRepository;

    @Override
    public boolean supports(InvoicePaymentRequest request) {
        return request.invoiceId() == null;
    }

    @Override
    public List<CustomerTransaction> process(
            InvoiceOperationContext ctx,
            InvoicePaymentRequest request,
            OffsetDateTime now
    ) {

        CustomerTransaction tx = customerTxRepository.save(
                CustomerTransaction.builder()
                        .branch(ctx.branch())
                        .customer(ctx.debtor())
                        .type(CustomerTransactionType.ADVANCE)
                        .amount(request.amount())
                        .createdAt(now)
                        .note(request.note())
                        .build()
        );

        return List.of(tx);
    }
}