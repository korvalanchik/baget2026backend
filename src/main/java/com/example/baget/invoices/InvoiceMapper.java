package com.example.baget.invoices;

import com.example.baget.orders.Orders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InvoiceMapper {

    public InvoiceDTO toDto(Invoice invoice) {
        if (invoice == null) return null;

        return InvoiceDTO.builder()
                .id(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(invoice.getCustomer().getCustNo())
                .customerName(invoice.getCustomer().getCompany()) // або інше поле
                .type(invoice.getType().name())
                .status(invoice.getStatus().name())
                .totalAmount(invoice.getTotalAmount())
                .createdAt(invoice.getCreatedAt())
                .note(invoice.getNote())
                .orders(invoice.getInvoiceOrders().stream()
                        .map(this::toOrderDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private InvoiceOrderDTO toOrderDto(InvoiceOrder io) {
        if (io == null) return null;

        Orders order = io.getOrder();
        return InvoiceOrderDTO.builder()
                .orderNo(order.getOrderNo())
                .amount(io.getAmount())
                .statusOrder(order.getStatusOrder())
                .shipDate(order.getShipDate())
                .build();
    }
}
