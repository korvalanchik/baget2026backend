package com.example.baget.customer;

import com.example.baget.orders.Orders;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;


@Entity
@Table (name = "customer")
@Getter
@Setter
public class Customer {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long custNo;

    @Column(length = 30)
    private String company;

    @Column(length = 30)
    private String addr1;

    @Column(length = 50)
    private String comment;

    @Column(length = 15)
    private String city;

    @Column(length = 20)
    private String state;

    @Column(length = 10)
    private String zip;

    @Column(length = 20)
    private String country;

    @Column(length = 15)
    private String phone;

    @Column(length = 15)
    private String mobile;

    @Column
    private Double taxRate;

    @Column(length = 20)
    private String contact;

    @Column
    private OffsetDateTime lastInvoiceDate;

    @Column
    private Double priceLevel;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Orders> orders = new ArrayList<>();

}
