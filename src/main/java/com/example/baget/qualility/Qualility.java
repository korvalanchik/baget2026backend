package com.example.baget.qualility;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "qualility")
@Entity
@Getter
@Setter
public class Qualility {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long keyType;

    @Column(length = 15)
    private String descriptQuolity;

}
