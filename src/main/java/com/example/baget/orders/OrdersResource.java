package com.example.baget.orders;

import com.example.baget.customer.CustomerInvoiceService;
import com.example.baget.invoices.InvoiceDTO;
import com.example.baget.invoices.IssueInvoiceFullRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrdersResource {

    private final OrdersService ordersService;
    private final CustomerInvoiceService customerInvoiceService;


    @GetMapping
    public Page<? extends OrderProjections.BaseOrdersView> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "1") String branch // <-- назва філіалу з фронту
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));
        return ordersService.getOrders(pageable, branch);
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<OrdersDTO> getOrder(@PathVariable(name = "orderNo") final Long orderNo) {
        return ResponseEntity.ok(ordersService.get(orderNo));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Long> createOrder(@RequestBody @Valid final OrdersDTO ordersDTO) {
        final Long createdOrderNo = ordersService.create(ordersDTO);
        return new ResponseEntity<>(createdOrderNo, HttpStatus.CREATED);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<OrderSaveResult>> saveBatch(@RequestBody List<OrdersDTO> orders) {
        List<OrderSaveResult> results = new ArrayList<>();

        for (OrdersDTO dto : orders) {
            try {
                ordersService.create(dto);
                results.add(new OrderSaveResult(dto, true, "Saved successfully"));
            } catch (Exception ex) {
                results.add(new OrderSaveResult(dto, false, "Failed: " + ex.getMessage()));
            }
        }

        return ResponseEntity.ok(results);
    }

    @PutMapping("/{orderNo}")
    public ResponseEntity<Long> updateOrder(@PathVariable(name = "orderNo") final Long orderNo,
            @RequestBody @Valid final OrdersDTO ordersDTO) {
        ordersService.update(orderNo, ordersDTO);
        return ResponseEntity.ok(orderNo);
    }

    @DeleteMapping("/{orderNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteOrder(@PathVariable(name = "orderNo") final Long orderNo) {
        ordersService.delete(orderNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public Page<OrderSummaryView> getOrderSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));
        return ordersService.getOrderSummaries(pageable);
    }

    @PostMapping("/{orderNo}/issue-invoice")
    public ResponseEntity<InvoiceDTO> issueInvoice(
            @PathVariable Long orderNo,
            @RequestBody IssueInvoiceFullRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(customerInvoiceService.issueInvoice(orderNo, request, authentication));
    }
}

