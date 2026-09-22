package com.example.baget.vendors;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(value = "/api/vendorss", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorsResource {

    private final VendorsService vendorsService;

    public VendorsResource(final VendorsService vendorsService) {
        this.vendorsService = vendorsService;
    }

    @GetMapping
    public ResponseEntity<List<VendorsDTO>> getAllVendorss() {
        return ResponseEntity.ok(vendorsService.findAll());
    }

    @GetMapping("/{vendorNo}")
    public ResponseEntity<VendorsDTO> getVendors(
            @PathVariable(name = "vendorNo") final Long vendorNo) {
        return ResponseEntity.ok(vendorsService.get(vendorNo));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Long> createVendors(@RequestBody @Valid final VendorsDTO vendorsDTO) {
        final Long createdVendorNo = vendorsService.create(vendorsDTO);
        return new ResponseEntity<>(createdVendorNo, HttpStatus.CREATED);
    }

    @PutMapping("/{vendorNo}")
    public ResponseEntity<Long> updateVendors(@PathVariable(name = "vendorNo") final Long vendorNo,
            @RequestBody @Valid final VendorsDTO vendorsDTO) {
        vendorsService.update(vendorNo, vendorsDTO);
        return ResponseEntity.ok(vendorNo);
    }

    @DeleteMapping("/{vendorNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteVendors(
            @PathVariable(name = "vendorNo") final Long vendorNo) {
        vendorsService.delete(vendorNo);
        return ResponseEntity.noContent().build();
    }

}
