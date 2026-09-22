package com.example.baget.finance;

import com.example.baget.branch.Branch;
import com.example.baget.customer.Customer;
import com.example.baget.invoices.Invoice;
import com.example.baget.users.User;

public record InvoiceOperationContext(
        User user,
        Branch branch,
        Customer debtor,
        Customer payer,
        Invoice invoice
) {}