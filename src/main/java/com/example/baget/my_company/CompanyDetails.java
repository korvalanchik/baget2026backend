package com.example.baget.my_company;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "company_details")
@Getter
@Setter
public class CompanyDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    private String initials;

    private String edrpou;

    private String phone;

    @Column(name = "bank_account")
    private String bankAccount;

    private String ipn;

    private String address;

    private String comment;

    @Column(name = "work_title")
    private String workTitle;

    @Column(name = "default_recipient")
    private String defaultRecipient;
}
