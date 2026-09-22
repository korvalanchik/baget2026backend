package com.example.baget.employee;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;


@Table(name = "employee")
@Entity
@Getter
@Setter
public class Employee {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer empNo;

    @Column(length = 20)
    private String lastName;

    @Column(length = 15)
    private String firstName;

    @Column(length = 4)
    private String phoneExt;

    @Column
    private OffsetDateTime hireDate;

    @Column
    private Double salary;

}
