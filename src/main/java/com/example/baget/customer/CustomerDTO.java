package com.example.baget.customer;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;


@Getter
@Setter
public class CustomerDTO {

    private Long custNo;

    @Size(max = 30)
    private String company;

    @Size(max = 30)
    private String addr1;

    @Size(max = 30)
    private String comment;

    @Size(max = 15)
    private String city;

    @Size(max = 20)
    private String state;

    @Size(max = 10)
    private String zip;

    @Size(max = 20)
    private String country;

    @Size(max = 15)
    private String phone;

    @Size(max = 15)
    private String mobile;

    private Double taxRate;

    @Size(max = 20)
    private String contact;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime lastInvoiceDate;

    private Double priceLevel;

    public CustomerDTO() {}

    public CustomerDTO(Long custNo, String company, String addr1, String mobile, String comment) {
        this.custNo = custNo;
        this.company = company;
        this.addr1 = addr1;
        this.mobile = mobile;
        this.comment = comment;
    }
}
