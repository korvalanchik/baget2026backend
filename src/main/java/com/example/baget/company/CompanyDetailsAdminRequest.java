package com.example.baget.company;

import jakarta.validation.constraints.Size;

public record CompanyDetailsAdminRequest(
        @Size(max = 255, message = "Найменування не може перевищувати 255 символів.")
        String fullName,

        @Size(max = 20, message = "ЄДРПОУ / РНОКПП не може перевищувати 20 символів.")
        String edrpou,

        @Size(max = 20, message = "ІПН платника ПДВ не може перевищувати 20 символів.")
        String ipn,

        @Size(max = 255, message = "Юридична адреса не може перевищувати 255 символів.")
        String address
) {
}
