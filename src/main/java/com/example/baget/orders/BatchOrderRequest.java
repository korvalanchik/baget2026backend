package com.example.baget.orders;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchOrderRequest {

    private List<OrdersDTO> orders;

}
