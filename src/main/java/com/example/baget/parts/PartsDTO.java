package com.example.baget.parts;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartsDTO {

    private Long partNo;

    private Long vendorNo;

    private String vendorName;

    @Size(max = 30)
    private String description;

    private Double profilWidth;

    private Double inQuality;

    private Double onHand;

    private Double onOrder;

    private Double cost;

    private Double listPrice;

    private Double listPrice1;

    private Double listPrice2;

    private Integer noPercent;

    private Double listPrice3;

    private Long version;
    public PartsDTO(Long partNo, String description) {
        this.partNo = partNo;
        this.description = description;
    }
}
