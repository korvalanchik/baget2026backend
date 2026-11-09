package com.example.baget.branch;

public record BranchAdminDTO(
        Long branchNo,
        String name,
        String address,
        boolean active
) {
}
