package com.example.baget.orders;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface OrderProjections {
    // базові поля для всіх користувачів
    interface BaseOrdersView {
        Long getOrderNo();

        @JsonProperty("custNo")
        Long getCustomer_CustNo();

        @JsonProperty("company")
        String getCustomer_Company();

        @JsonProperty("phone")
        String getCustomer_Mobile();

        @JsonProperty("addr1")
        String getCustomer_Addr1();

        @JsonProperty("branchName")
        String getBranch_Name(); // Orders.branch.name

        OffsetDateTime getSaleDate();

        OffsetDateTime getShipDate();

        @JsonProperty("empNo")
        String getEmployee_Username();

        BigDecimal getItemsTotal();

        Integer getStatusOrder();

    }

    // для адміну
    interface AdminOrderView extends BaseOrdersView {
        BigDecimal getTaxRate();
        BigDecimal getFreight();
        BigDecimal getAmountPaid();
        BigDecimal getAmountDueN();
        BigDecimal getIncome();
        BigDecimal getTotalCost();
        Integer getPriceLevel();
    }

    // для касира
    interface CounterOrderView extends BaseOrdersView {
        BigDecimal getAmountPaid();
    }

    // для звичайного користувача
    interface UserOrderView extends BaseOrdersView {
        String getNotice();
    }

    public interface OrderView {}

    interface PublicOrderView extends OrderView {
        Long getOrderNo();     // Номер рахунку
        @JsonProperty("branchName")
        String getBranch_Name(); // Orders.branch.name
        OffsetDateTime getSaleDate();     // Дата створення/виписки
    }
    interface PrivateOrderView extends PublicOrderView {
        @JsonProperty("company")
        String getCustomer_Company();

        @JsonProperty("phone")
        String getCustomer_Mobile();
        OffsetDateTime getShipDate();
        @JsonProperty("empNo")
        String getEmployee_Username();
        BigDecimal getItemsTotal();   // Загальна сума рахунку
        Integer getStatusOrder();
        String getNotice();
    }

}
