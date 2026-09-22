package com.example.baget.buh;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "Buh", indexes = {
        @Index(name = "count_index", columnList = "count")
})
@Getter
@Setter
public class Buh {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer count;

    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")    private LocalDateTime date;

    @Column(nullable = false)
    private Integer account;

    @Column(nullable = false)
    private Integer category;

    @Column(nullable = false)
    private Integer total;

    @Column(nullable = false)
    private Integer currency;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column
    private Integer transfer;

}
