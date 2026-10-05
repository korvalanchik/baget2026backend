package com.example.baget.warehouse;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.finance.CustomerPaymentAccess;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/warehouse/branch-orders")
@Transactional(readOnly=true)
public class BranchOrdersController {
 private final JdbcTemplate db;
 private final CustomerPaymentAccess access;
 public BranchOrdersController(JdbcTemplate db,CustomerPaymentAccess access){this.db=db;this.access=access;}
 private static final String HEADER="""
 SELECT o.OrderNo AS orderNo,o.StatusOrder AS status,o.CustNo AS customerId,o.BranchNo AS branchNo,
 c.Company AS company,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS mobile,b.BranchName AS branchName
 FROM orders o JOIN customer c ON c.CustNo=o.CustNo JOIN branches b ON b.BranchNo=o.BranchNo
 """;
 @GetMapping public Object list(@RequestParam(name="state",defaultValue="RECEIVE") String state,
   @RequestParam(name="page",defaultValue="0") int page,Authentication a){
  var user=access.user(a);
  if(!Set.of("RECEIVE","READY").contains(state) || page<0 || page>100000)throw failure(HttpStatus.BAD_REQUEST,"Некоректний розділ або сторінка");
  var args=new ArrayList<Object>();String scope="";
  if(!access.admin(user)){
   var branches=user.getAllowedBranches().stream().map(b->b.getBranchNo()).distinct().toList();
   if(branches.isEmpty())return Map.of("rows",List.of(),"hasMore",false);
   scope=" AND o.BranchNo IN ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")";args.addAll(branches);
  }
  String condition="RECEIVE".equals(state)?"p.pending_location='BRANCH'":"p.current_location='BRANCH' AND p.pending_location IS NULL";
  args.add(page*50);
  var rows=db.queryForList(HEADER+" WHERE o.StatusOrder NOT IN (5,10) "+scope+
   " AND EXISTS(SELECT 1 FROM order_products p WHERE p.order_no=o.OrderNo AND p.active=1 AND p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.active_rework_id IS NULL AND "+condition+") ORDER BY o.OrderNo DESC LIMIT 51 OFFSET ?",args.toArray());
  return Map.of("rows",rows.subList(0,Math.min(50,rows.size())),"hasMore",rows.size()>50);
 }
 @GetMapping("/{order}") public Object read(@PathVariable("order") long order,Authentication a){
  var user=access.user(a);
  var branches=db.queryForList("SELECT BranchNo FROM orders WHERE OrderNo=?",Long.class,order);
  if(branches.isEmpty())throw failure(HttpStatus.NOT_FOUND,"Замовлення не знайдено");
  access.branch(user,branches.get(0));
  var rows=db.queryForList(HEADER+" WHERE o.OrderNo=?",order);
  if(rows.isEmpty())throw failure(HttpStatus.NOT_FOUND,"Картка замовлення недоступна");
  var result=rows.get(0);
  result.put("products",db.queryForList("""
   SELECT id,client_product_id AS productName,tracking_id AS trackingId,tracking_revision AS revision,
   production_state AS state,production_confirmed AS confirmed,current_location AS location,pending_location AS pending,
   active_rework_id AS reworkId,measured_width_mm AS widthMm,measured_height_mm AS heightMm
   FROM order_products WHERE order_no=? AND active=1 ORDER BY id
   """,order));
  result.put("canReceive",user.getRoles().stream().anyMatch(r->Set.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER").contains(r.getName())));
  return result;
 }
 private static PreviewException failure(HttpStatus status,String message){return new PreviewException(status,"BRANCH_ORDERS",message);}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
