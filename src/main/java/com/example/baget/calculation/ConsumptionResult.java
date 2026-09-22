package com.example.baget.calculation;

import com.example.baget.parts.UnitType;
import java.math.BigDecimal;

public record ConsumptionResult(
        BigDecimal consumptionPerProduct,
        int productQuantity,
        BigDecimal totalConsumption,
        UnitType unit
) {}
