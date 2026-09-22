package com.example.baget.vendors;

import com.example.baget.parts.Parts;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "vendors")
@Getter
@Setter
public class Vendors {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vendorNo;

    @Column(length = 30)
    private String vendorName;

    @Column(length = 30)
    private String address1;

    @Column(length = 30)
    private String address2;

    @Column(length = 20)
    private String city;

    @Column(length = 20)
    private String state;

    @Column(length = 10)
    private String zip;

    @Column(length = 15)
    private String country;

    @Column(length = 15)
    private String phone;

    @Column(length = 15)
    private String fax;

    @Column
    private Integer preferred;

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Parts> parts = new ArrayList<>();


}
