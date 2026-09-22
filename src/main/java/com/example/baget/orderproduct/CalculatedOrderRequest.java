package com.example.baget.orderproduct;

import com.example.baget.calculation.estimate.EstimatePreviewRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record CalculatedOrderRequest(
    @NotNull @Pattern(regexp="[a-fA-F0-9-]{36}") String requestKey,
    @NotBlank @Size(max=50) String branchName,
    @Positive Long custNo,
    @Size(max=30) String company,
    @Size(max=15) String phone,
    @Size(max=30) String address,
    @Size(max=100) String notice,
    LocalDate dueDate,
    @NotNull @PositiveOrZero Long expectedRevision,
    @NotNull @Size(min=1,max=50) List<@NotNull @Valid Product> products
) {
    public record Product(
        @NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String clientProductId,
        @NotNull JsonNode form,
        @Size(max=500) String note,
        @NotNull @Size(min = 1, max = 3) List<EstimatePreviewRequest.@NotNull @Valid Item> components
    ) {}
}
