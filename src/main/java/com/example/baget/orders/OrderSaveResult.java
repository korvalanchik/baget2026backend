package com.example.baget.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrderSaveResult {

    private OrdersDTO order;

    private boolean success;

    private String message;
}
