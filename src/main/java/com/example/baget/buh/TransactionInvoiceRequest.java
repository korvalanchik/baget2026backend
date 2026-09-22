package com.example.baget.buh;

import java.math.BigDecimal;

public record TransactionInvoiceRequest(Long invoiceNo, BigDecimal amount) {}
