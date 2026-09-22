package com.example.baget.parts;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class BagetResponseDTO {
    private List<ProfilListDTO> profileParts;
    private List<AccessoryListDTO> accessoryParts;
}
