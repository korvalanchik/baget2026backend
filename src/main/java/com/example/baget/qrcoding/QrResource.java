package com.example.baget.qrcoding;

import com.example.baget.orders.OrderProjections;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.util.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/qrcode/invoices")
public class QrResource {

    private final ScanService scanService;
    private final QrGenerator qrGenerator;
    private final OrdersRepository ordersRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    @GetMapping("/{id}/qr.png")
    public ResponseEntity<byte[]> getInvoiceQr(@PathVariable Long id) throws Exception {
        Optional<Orders> inv = Optional.ofNullable(ordersRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id)));
        String url = frontendUrl + "/orders/order-info.html?publicId=" + inv.get().getPublicId();

        byte[] png = qrGenerator.generateQrPng(url, 300, 300);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(png, headers, HttpStatus.OK);
    }

    @GetMapping("/{orderNo}/publicId")
    public ResponseEntity<String> getPublicId(@PathVariable(name = "orderNo") final Long orderNo) {
        Orders order = ordersRepository.findById(orderNo)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + orderNo));
        return ResponseEntity.ok(order.getPublicId());
    }

    @GetMapping("/public/{publicId}")
    public ResponseEntity<OrderProjections.OrderView> getPublicOrder(@PathVariable String publicId, Principal principal) {
        OrderProjections.OrderView result = scanService.scanOrder(publicId, principal);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/scan/{publicId}/status")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable String publicId,
                                                  @RequestParam Integer status,
                                                  Principal principal) {
        scanService.updateOrderStatus(publicId, status, principal);
        return ResponseEntity.noContent().build();
    }

}