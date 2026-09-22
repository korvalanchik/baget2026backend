package com.example.baget.customer;

import com.example.baget.orders.Orders;

import java.math.BigDecimal;

public final class CustomerTxFactory {

    private CustomerTxFactory() {}

    public static CustomerTransaction invoice(Customer customer, Orders order, BigDecimal amount, String reference) {
        return CustomerTransaction.builder()
                .customer(customer)
                .order(order)
                .type(CustomerTransactionType.INVOICE)
                .amount(amount.abs()) // завжди +
                .reference(reference)
                .note("Виставлено рахунок на замовлення №" + order.getOrderNo())
                .build();
    }

    public static CustomerTransaction payment(Customer customer, Orders order, BigDecimal amount, String reference) {
        return CustomerTransaction.builder()
                .customer(customer)
                .order(order)
                .type(CustomerTransactionType.PAYMENT)
                .amount(amount.abs().negate()) // завжди -
                .reference(reference)
                .note("Оплата замовлення №" + (order != null ? order.getOrderNo() : "—"))
                .build();
    }
}

