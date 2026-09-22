package com.example.baget.rahfact;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "rahfact")
@Entity
@Getter
@Setter
public class Rahfact {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long numRahFact;

    @Column
    private Long numOrders;

    @Column
    private Double sumOrders;

}
