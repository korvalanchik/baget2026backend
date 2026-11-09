package com.example.baget.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchAdminRequest(
        @NotBlank(message = "Вкажіть назву філії.")
        @Size(max = 50, message = "Назва філії не може перевищувати 50 символів.")
        String name,

        @Size(max = 255, message = "Адреса не може перевищувати 255 символів.")
        String address,

        Boolean active
) {
}
