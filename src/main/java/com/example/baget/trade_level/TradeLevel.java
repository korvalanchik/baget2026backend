package com.example.baget.trade_level;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "TradeLevel")
@Entity
@Getter
@Setter
public class TradeLevel {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer noLevel;

    @Column(length = 22)
    private String level;

}
