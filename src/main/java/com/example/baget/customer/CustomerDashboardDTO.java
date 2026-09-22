package com.example.baget.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerDashboardDTO {

    public record Response(
            List<WithoutInvoice> withoutInvoice,
            List<Debtor> debtors,
            List<Payer> payers
    ) {}

    public record WithoutInvoice(
            Long customerId,
            String company,
            String mobile,
            Integer pendingOrders,
            BigDecimal balance
    ) {}

    public record Debtor(
            Long customerId,
            String company,
            String mobile,
            BigDecimal balance,
            Integer invoiceCount,
            LocalDateTime lastPaymentDate
    ) {}

    public record Payer(
            Long customerId,
            String company,
            String mobile,
            Integer consolidatedInvoices,
            BigDecimal totalTurnover
    ) {}
}
