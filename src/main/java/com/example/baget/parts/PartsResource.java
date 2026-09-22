package com.example.baget.parts;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(value = "/api/parts", produces = MediaType.APPLICATION_JSON_VALUE)
public class PartsResource {

    private final PartsService partsService;

    public PartsResource(final PartsService partsService) {
        this.partsService = partsService;
    }

    @GetMapping
    public Page<PartsDTO> getAllParts(
            @RequestParam(defaultValue = "0") int page,   // Номер сторінки, за замовчуванням 0
            @RequestParam(defaultValue = "10") int size   // Розмір сторінки, за замовчуванням 10
    ) {
        Pageable pageable = PageRequest.of(page, size);  // Створення об'єкта Pageable для пагінації
        return partsService.getParts(pageable);  // Повертаємо сторінку елементів
    }

    @GetMapping("/{partNo}")
    public ResponseEntity<PartsDTO> getPart(@PathVariable(name = "partNo") final Long partNo) {
        return ResponseEntity.ok(partsService.get(partNo));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Long> createPart(@RequestBody @Valid final PartsDTO partsDTO) {
        final Long createdPartNo = partsService.create(partsDTO);
        return new ResponseEntity<>(createdPartNo, HttpStatus.CREATED);
    }

    @PutMapping("/{partNo}")
    public ResponseEntity<Long> updatePart(@PathVariable(name = "partNo") final Long partNo,
            @RequestBody @Valid final PartsDTO partsDTO) {
        partsService.update(partNo, partsDTO);
        return ResponseEntity.ok(partNo);
    }

    @DeleteMapping("/{partNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deletePart(@PathVariable(name = "partNo") final Long partNo) {
        partsService.delete(partNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profillist")
    public ResponseEntity<?> getBaget() {
        return partsService.getBaget();
    }
}
