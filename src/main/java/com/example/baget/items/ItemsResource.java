package com.example.baget.items;

import com.example.baget.orders.OrdersDTO;
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
@RequestMapping(value = "/api/orders/{orderNo}/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class ItemsResource {

    private final ItemsService itemsService;

    public ItemsResource(final ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @GetMapping("")
    public ResponseEntity<List<ItemsDTO>> getItemsByOrder(@PathVariable(name = "orderNo") final Long orderNo) {
        return ResponseEntity.ok(itemsService.findByOrderNo(orderNo));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Long> createItem(@PathVariable(name = "orderNo") final Long orderNo,
                                           @RequestBody @Valid final List<ItemsDTO> itemsDTO) {
//        itemsService.create(itemsDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/{itemNo}")
    public ResponseEntity<ItemsDTO> getItem(@PathVariable(name = "orderNo") final Long orderNo,
                                             @PathVariable(name = "itemNo") final Long itemNo) {
        return ResponseEntity.ok(itemsService.get(orderNo, itemNo));
    }

    @PutMapping("/{itemNo}")
    public ResponseEntity<Long> updateItem(@PathVariable(name = "orderNo") final Long orderNo,
                                            @PathVariable(name = "itemNo") final Long itemNo,
            @RequestBody @Valid final ItemsDTO itemsDTO) {
        itemsService.update(orderNo, itemNo, itemsDTO);
        return ResponseEntity.ok(orderNo);
    }

    @DeleteMapping("/{itemNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteItems(@PathVariable(name = "orderNo") final Long orderNo,
                                            @PathVariable(name = "itemNo") final Long itemNo) {
        itemsService.delete(orderNo, itemNo);
        return ResponseEntity.noContent().build();
    }

}
