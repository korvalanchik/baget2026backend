package com.example.baget.parts;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProfilListDTO {
    private Long partNo;

    @Size(max = 30)
    private String description;

    private Long profilWidth;

    private Double onHand;

    private Double listPrice;
}
