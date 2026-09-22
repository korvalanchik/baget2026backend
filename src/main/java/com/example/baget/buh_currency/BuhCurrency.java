package com.example.baget.buh_currency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "Buh_currency")
@Entity
@Getter
@Setter
public class BuhCurrency {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer currencyNo;

    @Column(nullable = false, columnDefinition = "longtext")
    private String currency;

}
