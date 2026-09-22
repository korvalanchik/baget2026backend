package com.example.baget.items;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemViewDTO {

    private Long partNo;
    private String description;

    private Double width;
    private Double height;

    private Double quantity;
    private Double qty;

    private BigDecimal sellPrice;
    private BigDecimal discount;

    private BigDecimal total;
}