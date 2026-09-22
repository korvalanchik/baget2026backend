package com.example.baget.orders;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class OrderCounterDTO extends BaseOrdersDTO {

    private BigDecimal amountPaid;

    private BigDecimal amountDueN;

    private BigDecimal totalCost;

}
