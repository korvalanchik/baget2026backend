package com.example.baget.warehouse;

import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/warehouse/materials")
public class MaterialCardController {
 private final JdbcTemplate db;
 private final WarehouseService warehouse;
 public MaterialCardController(JdbcTemplate db, WarehouseService warehouse) {this.db=db;this.warehouse=warehouse;}
 private static final String MOVEMENTS = """
 SELECT CONCAT('S',s.id) AS eventKey,s.created_at AS createdAt,s.operation AS type,
 s.stock_after-s.stock_before AS delta,s.stock_before AS stockBefore,s.stock_after AS stockAfter,
 s.actor,s.note,NULL AS orderNo,NULL AS productId,NULL AS reworkId,
 sl.document_id AS stocktakeId,sr.order_id AS supplierOrderId,sr.document_no AS receiptNo
 FROM material_stock_operations s
 LEFT JOIN stocktake_posted_lines sl ON sl.stock_operation_key=s.request_key
 LEFT JOIN supplier_receipt_lines rl ON rl.stock_operation_key=s.request_key
 LEFT JOIN supplier_receipts sr ON sr.id=rl.receipt_id WHERE s.part_no=?
 UNION ALL
 SELECT CONCAT('I',m.id),m.created_at,IF(i.rework_id IS NULL,'PRODUCTION','REWORK'),
 m.quantity_delta,m.stock_before,m.stock_after,m.actor,'',m.order_no,i.order_product_id,
 i.rework_id,NULL,NULL,NULL
 FROM inventory_movements m JOIN items i ON i.OrderNo=m.order_no AND i.ItemNo=m.item_no
 WHERE m.part_no=?
 UNION ALL
 SELECT CONCAT('R',e.id),e.created_at,'RESERVATION',e.reserved_after-e.reserved_before,
 e.reserved_before,e.reserved_after,e.actor,e.reason,p.order_no,r.order_product_id,NULL,NULL,NULL,NULL
 FROM reservation_events e JOIN material_reservations r ON r.id=e.reservation_id
 JOIN order_products p ON p.id=r.order_product_id WHERE r.part_no=?
 """;
 @GetMapping("/{id}/card")
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object card(@PathVariable("id") long id,
  @RequestParam(name="from",required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
  @RequestParam(name="to",required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
  @RequestParam(name="type",defaultValue="ALL") String type,
  @RequestParam(name="page",defaultValue="0") int page, Authentication auth) {
  // Existing service enforces database roles and material eligibility before any history query.
  var detail=warehouse.material(id,auth);
  if(page<0||page>100000||!Set.of("ALL","RECEIPT","COUNT","PRODUCTION","REWORK","RESERVATION").contains(type)
    ||(from!=null&&to!=null&&from.isAfter(to))|| (to!=null&&to.equals(LocalDate.MAX)))
   throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Перевірте період і тип операції");
  var result=new LinkedHashMap<String,Object>();
  for(String key:List.of("partNo","description","unit","onHand","reservations"))result.put(key,detail.get(key));
  var sums=db.queryForMap("SELECT COALESCE(SUM(demand),0) AS demand,COALESCE(SUM(reserved),0) AS reserved FROM material_reservations WHERE part_no=? AND active=1",id);
  var demand=new BigDecimal(sums.get("demand").toString());var reserved=new BigDecimal(sums.get("reserved").toString());
  result.putAll(sums);result.put("shortage",demand.subtract(reserved).max(BigDecimal.ZERO));
  result.put("available",detail.get("onHand")==null?null:new BigDecimal(detail.get("onHand").toString()).subtract(reserved));
  var incoming=db.queryForList("""
   SELECT o.id AS supplierOrderId,o.expected_date AS expectedDate,o.status,
   l.ordered_qty-l.received_qty AS quantity FROM supplier_order_lines l
   JOIN supplier_orders o ON o.id=l.order_id WHERE l.part_no=? AND o.status IN ('ORDERED','PARTIAL')
   AND l.ordered_qty>l.received_qty ORDER BY o.expected_date,o.id
   """,id);
  result.put("incoming",incoming);
  result.put("expected",incoming.stream().map(r->new BigDecimal(r.get("quantity").toString())).reduce(BigDecimal.ZERO,BigDecimal::add));
  var args=new ArrayList<Object>(List.of(id,id,id));
  var sql=new StringBuilder("SELECT * FROM ("+MOVEMENTS+") movements WHERE 1=1");
  if(from!=null){sql.append(" AND createdAt>=?");args.add(from.atStartOfDay());}
  if(to!=null){sql.append(" AND createdAt<?");args.add(to.plusDays(1).atStartOfDay());}
  if(!type.equals("ALL")){sql.append(" AND type=?");args.add(type);}
  sql.append(" ORDER BY createdAt DESC,eventKey DESC LIMIT 101 OFFSET ?");args.add(page*100);
  var events=db.queryForList(sql.toString(),args.toArray());
  result.put("hasMore",events.size()>100);result.put("events",events.subList(0,Math.min(100,events.size())));
  result.put("page",page);
  return result;
 }
 @GetMapping("/{id}/orders/{orderNo}")
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object order(@PathVariable("id") long id,@PathVariable("orderNo") long orderNo,Authentication auth) {
  warehouse.material(id,auth);
  return db.queryForList("""
   SELECT i.ItemNo AS itemNo,i.order_product_id AS productId,i.rework_id AS reworkId,
   i.planned_consumption AS planned,i.actual_consumption AS actual,
   i.actual_width_mm AS widthMm,i.actual_height_mm AS heightMm,
   p.production_state AS state FROM items i
   LEFT JOIN order_products p ON p.id=i.order_product_id
   WHERE i.OrderNo=? AND i.PartNo=? ORDER BY i.ItemNo
   """,orderNo,id);
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> fail(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Матеріал не знайдено"));}
}
