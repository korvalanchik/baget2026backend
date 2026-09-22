package com.example.baget.orders;

import com.example.baget.items.ItemsDTO;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;


@Getter
@Setter
public class OrdersDTO {

    private Long orderNo;
    private long calculationRevision;

    private Long custNo;

    @Size(max = 30)
    private String company;

    @Size(max = 15)
    private String phone;

    @Size(max = 30)
    private String addr1;

    private List<ItemsDTO> items;

    private String branchName;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime saleDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime shipDate;

    private String empNo;

    private OffsetDateTime receivedDate;

    private OffsetDateTime clientReceivedDate;

    @Size(max = 30)
    private String shipToAddr2;

    @Size(max = 15)
    private String shipToCity;

    @Size(max = 20)
    private String shipToState;

    @Size(max = 10)
    private String shipToZip;

    @Size(max = 20)
    private String shipToCountry;

    @Size(max = 15)
    private String shipToPhone;

    @Size(max = 7)
    private String shipVia;

    @Size(max = 15)
    private String po;

    @Size(max = 6)
    private String terms;

    @Size(max = 7)
    private String paymentMethod;

    private BigDecimal itemsTotal;

    private BigDecimal taxRate;

    private BigDecimal freight;

    private BigDecimal amountPaid;

    private BigDecimal amountDueN;

    private BigDecimal income;

    private BigDecimal totalCost;

    private Integer priceLevel;

    private Integer statusOrder;

    private Long rahFacNo;

    @Size(max = 100)
    private String notice;

}
