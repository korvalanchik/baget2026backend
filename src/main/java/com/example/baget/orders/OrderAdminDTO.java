package com.example.baget.orders;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class OrderAdminDTO extends BaseOrdersDTO {

    private BigDecimal taxRate;

    private BigDecimal amountPaid;

    private BigDecimal amountDueN;

    private BigDecimal income;

    private BigDecimal totalCost;

    private Integer priceLevel;

}
