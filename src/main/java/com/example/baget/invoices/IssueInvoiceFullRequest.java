package com.example.baget.invoices;

import com.example.baget.customer.CustomerIssueInvoiceRequestDTO;
import com.example.baget.orders.OrdersDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueInvoiceFullRequest {

    private OrdersDTO order;      // 🔹 для Orders
    private CustomerIssueInvoiceRequestDTO invoice;  // 🔹 для Invoice

}