package com.example.baget.items;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ItemsDTO {

    private Long orderNo;

    @NotNull
    private Long itemNo;

    private Long partNo;

    private String description;

    private Double profilWidth;

    private Double estimationWidth;

    private Double estimationHeight;

    private Double width;

    private Double height;

    private Double qty;

    private Double quantity;

    private Double sellPrice;

    private Double resellerPrice;

    private Double discount;

    private Double onHand;

    private Double cost;

    private Double sum;
}
