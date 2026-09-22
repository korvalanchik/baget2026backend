package com.example.baget.orders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface OrderPublicSummaryView {
    Long getOrderNo();     // Номер рахунку
    OffsetDateTime getSaleDate();     // Дата створення/виписки
    BigDecimal getItemsTotal();   // Загальна сума рахунку
}
