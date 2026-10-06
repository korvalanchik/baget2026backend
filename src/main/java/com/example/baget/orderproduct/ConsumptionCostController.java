package com.example.baget.orderproduct;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
@RestController
@RequestMapping("/api/warehouse/products/{product}/consumption-cost")
public class ConsumptionCostController {
 private final JdbcTemplate db;private final UsersRepository users;
 public ConsumptionCostController(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 @GetMapping
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Object read(@PathVariable("product") long product,Authentication a){
  if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"COST_ACCESS","Складська вартість виробу доступна адміністратору");
  if(db.queryForList("SELECT id FROM order_products WHERE id=?",product).isEmpty())throw new PreviewException(HttpStatus.NOT_FOUND,"PRODUCT_MISSING","Виріб не знайдено");
  var rows=db.queryForList("SELECT i.OrderNo AS orderNo,i.ItemNo AS itemNo,i.PartNo AS partNo,p.Description AS description,i.rework_id AS reworkId,-m.quantity_delta AS quantity,COALESCE(m.inventory_cost_unit,i.calculation_unit) AS unit,m.inventory_unit_cost AS unitCost,m.inventory_cost_captured_at AS capturedAt,m.created_at AS consumedAt FROM inventory_movements m JOIN items i ON i.OrderNo=m.order_no AND i.ItemNo=m.item_no LEFT JOIN parts p ON p.PartNo=i.PartNo WHERE i.order_product_id=? AND m.quantity_delta<0 ORDER BY i.ItemNo",product);
  BigDecimal total=BigDecimal.ZERO;int unknown=0;
  for(var row:rows){BigDecimal amount=null;if(row.get("unitCost")==null)unknown++;else {amount=new BigDecimal(row.get("quantity").toString()).multiply(new BigDecimal(row.get("unitCost").toString()));total=total.add(amount);}row.put("amount",amount);}
  var result=new LinkedHashMap<String,Object>();result.put("rows",rows);result.put("unknownCount",unknown);result.put("knownTotal",total.setScale(2,RoundingMode.HALF_UP));result.put("total",unknown==0&&!rows.isEmpty()?total.setScale(2,RoundingMode.HALF_UP):null);return result;
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
