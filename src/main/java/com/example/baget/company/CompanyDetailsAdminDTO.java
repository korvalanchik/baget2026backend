package com.example.baget.company;

import java.util.List;

public record CompanyDetailsAdminDTO(
        String fullName,
        String edrpou,
        String ipn,
        String address,
        boolean readyForReceipts,
        List<String> warnings
) {
}
