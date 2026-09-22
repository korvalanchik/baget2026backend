package com.example.baget.buh_category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "Buh_category")
@Entity
@Getter
@Setter
public class BuhCategory {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer categoryNo;

    @Column(nullable = false, columnDefinition = "longtext")
    private String category;

}
