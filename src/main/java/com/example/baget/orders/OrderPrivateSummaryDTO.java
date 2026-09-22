package com.example.baget.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
@Getter
@Setter
@AllArgsConstructor
public class OrderPrivateSummaryDTO {
    private Long orderNo;

    private Long custNo;

    private String company;

    private String phone;
    private String branchName;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime saleDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime shipDate;

    private String empNo;

    private BigDecimal itemsTotal;

    private Integer statusOrder;

    private String notice;

}
