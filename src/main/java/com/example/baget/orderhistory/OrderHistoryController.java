package com.example.baget.orderhistory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController
@RequestMapping("/api/order-history")
public class OrderHistoryController {
 private final OrderHistoryService service;
 public OrderHistoryController(OrderHistoryService service){this.service=service;}
 @GetMapping("/search") public Object search(@RequestParam(name="q",defaultValue="") String q,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.search(q,page,a);}
 @GetMapping("/{order}") public Object read(@PathVariable("order") long order,@RequestParam(name="group",defaultValue="ALL") String group,@RequestParam(name="product",required=false) Long product,@RequestParam(name="page",defaultValue="0") int page,Authentication a){return service.read(order,group,product,page,a);}
}
