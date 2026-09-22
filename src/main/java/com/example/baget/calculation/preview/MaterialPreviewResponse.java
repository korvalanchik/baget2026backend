package com.example.baget.calculation.preview;

import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import java.math.BigDecimal;

public record MaterialPreviewResponse(
        Long partNo, String description, CalculationMethod calculationMethod,
        UnitType unit, BigDecimal consumptionPerProduct, int productQuantity,
        BigDecimal totalConsumption, BigDecimal unitPrice, BigDecimal amount,
        String currency
) {}
