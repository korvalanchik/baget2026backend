package com.example.baget.status;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "status")
@Entity
@Getter
@Setter
public class Status {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer statusNo;

    @Column(length = 10)
    private String statusName;

}
