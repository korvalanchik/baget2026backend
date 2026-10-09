package com.example.baget.invoices;

import java.math.BigDecimal;

public record InvoicePaymentRequest(
        Long branchNo,
        Long invoiceId,
        Long customerId,
        BigDecimal amount,
        BigDecimal allocationAmount,  // списання авансу
        String note,
        Long cashAccountId
) {
    public InvoicePaymentRequest(Long branchNo,Long invoiceId,Long customerId,BigDecimal amount,BigDecimal allocationAmount,String note){this(branchNo,invoiceId,customerId,amount,allocationAmount,note,null);}
}
