package com.example.baget.buh_account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Table(name = "Buh_account")
@Entity
@Getter
@Setter
public class BuhAccount {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer accountNo;

    @Column(nullable = false, columnDefinition = "longtext")
    private String account;

}
