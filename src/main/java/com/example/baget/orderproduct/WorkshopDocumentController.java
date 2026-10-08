package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.commercial.CommercialService;
import com.example.baget.qrcoding.QrGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** A consistent, saved snapshot. Does not save measurements, move products or issue invoices. */
@RestController
@RequestMapping("/api/calculated-orders/{orderNo}/workshop/{productId}/document")
public class WorkshopDocumentController {
 private final WorkshopService workshop;
 private final CalculatedOrderService orders;
 private final CommercialService commercial;
 private final JdbcTemplate db;
 private final QrGenerator qr;
 @Value("${frontend.product-tracking-url:${frontend.url}}") private String frontend;
 public WorkshopDocumentController(WorkshopService workshop,CalculatedOrderService orders,CommercialService commercial,JdbcTemplate db,QrGenerator qr){this.workshop=workshop;this.orders=orders;this.commercial=commercial;this.db=db;this.qr=qr;}
 @GetMapping
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<?> read(@PathVariable("orderNo") long orderNo,@PathVariable("productId") long productId,Authentication a)throws Exception{
  var view=workshop.view(orderNo,a); // Existing authentication and branch checks.
  var p=view.products().stream().filter(x->x.id()==productId).findFirst().orElseThrow(()->new PreviewException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Виріб не належить цьому замовленню"));
  var order=orders.read(orderNo,a);
  var header=new LinkedHashMap<String,Object>();header.put("orderNo",orderNo);header.put("branchName",order.branchName());header.put("dueDate",order.dueDate());header.put("notice",order.notice());header.put("status",order.statusOrder());header.put("revision",view.revision());
  var result=new LinkedHashMap<String,Object>();result.put("header",header);result.put("product",p);result.put("commercial",commercial.read(productId,a));result.put("generatedAt",OffsetDateTime.now(ZoneId.of("Europe/Kyiv")));
  boolean labelAllowed=labelAllowed(view.status(),p.state(),p.confirmed(),p.rework()!=null&&p.rework().get("active")!=null);
  result.put("labelAllowed",labelAllowed);
  if(labelAllowed){
   String token=db.queryForObject("SELECT tracking_id FROM order_products WHERE id=? AND order_no=? AND active=1",String.class,productId,orderNo);
   if(token==null||token.isBlank())throw new PreviewException(HttpStatus.CONFLICT,"TRACKING_MISSING","Для виробу відсутній QR-ідентифікатор");
   String url=frontend.replaceAll("/+$","")+"/?product="+token;
   result.put("qrImage","data:image/png;base64,"+Base64.getEncoder().encodeToString(qr.generateQrPng(url,320,320)));
  }
  return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(result);
 }
 static boolean labelAllowed(int status,WorkshopRules.State state,boolean confirmed,boolean activeRework){return !Set.of(5,10).contains(status)&&state==WorkshopRules.State.COMPLETED&&confirmed&&!activeRework;}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
