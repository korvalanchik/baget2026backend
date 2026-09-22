package com.example.baget.vendors;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class VendorsDTO {

    private Long vendorNo;

    @Size(max = 30)
    private String vendorName;

    @Size(max = 30)
    private String address1;

    @Size(max = 30)
    private String address2;

    @Size(max = 20)
    private String city;

    @Size(max = 20)
    private String state;

    @Size(max = 10)
    private String zip;

    @Size(max = 15)
    private String country;

    @Size(max = 15)
    private String phone;

    @Size(max = 15)
    private String fax;

    private Integer preferred;

}
