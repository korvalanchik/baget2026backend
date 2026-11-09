package com.example.baget.branch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchDTO {

    private Long branchNo;
    private String name;
    private String address;

    public BranchDTO(Long branchNo, String name) {
        this(branchNo, name, null);
    }
}
