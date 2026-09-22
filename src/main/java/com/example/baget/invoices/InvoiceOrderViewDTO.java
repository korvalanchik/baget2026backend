package com.example.baget.invoices;

import com.example.baget.items.ItemViewDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class InvoiceOrderViewDTO {

    private Long orderNo;

    private BigDecimal amount;

    private String branchName;

    private OffsetDateTime saleDate;

    private OffsetDateTime shipDate;

    private List<ItemViewDTO> items;
}