package com.example.baget.finance;

import com.example.baget.customer.CustomerTransaction;
import com.example.baget.invoices.InvoicePaymentRequest;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentProcessor {

    boolean supports(InvoicePaymentRequest request);

    List<CustomerTransaction> process(
            InvoiceOperationContext ctx,
            InvoicePaymentRequest request,
            OffsetDateTime now
    );
}