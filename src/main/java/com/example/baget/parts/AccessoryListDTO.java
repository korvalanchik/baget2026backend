package com.example.baget.parts;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AccessoryListDTO {
    private Long partNo;
    private String description;
    private Double listPrice;
}
