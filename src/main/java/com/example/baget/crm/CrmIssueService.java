package com.example.baget.crm;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.finance.ProductPaymentCheck;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CrmIssueService {
 private final JdbcTemplate db;private final CrmService crm;private final CustomerPaymentAccess access;private final ProductPaymentCheck payment;private final ObjectMapper json;
 public CrmIssueService(JdbcTemplate db,CrmService crm,CustomerPaymentAccess access,ProductPaymentCheck payment,ObjectMapper json){this.db=db;this.crm=crm;this.access=access;this.payment=payment;this.json=json;}
 private void authorize(long customer,long branch,Authentication a){access.branch(access.user(a),branch);crm.customer(customer,branch,a);}
 private Map<String,Object> order(long customer,long branch,long order,boolean lock){
  var rows=db.queryForList("SELECT OrderNo AS orderNo,StatusOrder AS status FROM orders WHERE OrderNo=? AND CustNo=? AND BranchNo=?"+(lock?" FOR UPDATE":""),order,customer,branch);
  if(rows.isEmpty())throw fail("Замовлення не належить цьому клієнту та філії");return rows.get(0);
 }
 private static final String PRODUCTS="SELECT id,client_product_id AS productName,production_state AS state,production_confirmed AS confirmed,current_location AS location,pending_location AS pending,tracking_revision AS revision,active_rework_id AS reworkId,actual_total AS amount,measured_width_mm AS widthMm,measured_height_mm AS heightMm FROM order_products WHERE order_no=? AND active=1 ORDER BY id";
 public Map<String,Object> read(long customer,long branch,long order,Authentication a){
  authorize(customer,branch,a);var header=order(customer,branch,order,false);boolean cancelled=Set.of(5L,10L).contains(num(header,"status"));
  var rows=db.queryForList(PRODUCTS,order);
  for(var p:rows){String blocked=cancelled?"Замовлення скасоване":CrmIssueRules.blocked(p);p.put("blocked",blocked);if(blocked==null){var finance=payment.read(num(p,"id"));p.put("payment",finance);p.put("canIssue",Boolean.TRUE.equals(finance.get("canIssue")));}else p.put("canIssue",false);}
  header.put("products",rows);header.put("customerId",customer);header.put("branchNo",branch);return header;
 }
 /** Resolve a label without exposing contacts until role and branch checks succeed. */
 public Map<String,Object> resolveQr(String token,Authentication a){
  var user=access.user(a);
  try{if(token==null || !UUID.fromString(token).toString().equalsIgnoreCase(token))throw new IllegalArgumentException();}
  catch(IllegalArgumentException ex){throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_PRODUCT_QR","Некоректний QR-код виробу");}
  var rows=db.queryForList("SELECT p.id AS productId,o.OrderNo AS orderNo,o.CustNo AS customerId,o.BranchNo AS branchNo FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.tracking_id=? AND p.active=1",token.toLowerCase(Locale.ROOT));
  if(rows.isEmpty())throw new PreviewException(HttpStatus.NOT_FOUND,"PRODUCT_QR_NOT_FOUND","Виріб за QR-кодом не знайдено");
  var result=new HashMap<String,Object>(rows.get(0));long customer=num(result,"customerId"),branch=num(result,"branchNo");
  access.branch(user,branch);crm.customer(customer,branch,a);
  var contacts=db.queryForList("SELECT c.Company AS company,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS mobile,b.BranchName AS branchName FROM customer c CROSS JOIN branches b WHERE c.CustNo=? AND b.BranchNo=?",customer,branch);
  if(contacts.isEmpty())throw new PreviewException(HttpStatus.NOT_FOUND,"PRODUCT_QR_NOT_FOUND","Контакти клієнта або філію не знайдено");
  result.putAll(contacts.get(0));return result;
 }
 public record Selection(@Positive long productId,@PositiveOrZero long revision) {}
 public record Issue(@NotBlank @Size(max=36) String requestKey,@NotEmpty @Size(max=100) List<@NotNull @Valid Selection> products,
  boolean externalPaymentChecked,@Size(max=1000) String note) {}
 public Object issue(long customer,long branch,long order,Issue r,Authentication a){
  authorize(customer,branch,a);
  try{if(!UUID.fromString(r.requestKey()).toString().equals(r.requestKey()))throw new IllegalArgumentException();}catch(IllegalArgumentException ex){throw fail("Некоректний ключ операції");}
  if(retry(customer,branch,order,r,a))return Map.of("issued",true,"duplicate",true);
  order(customer,branch,order,false);
  var requested=new TreeMap<Long,Long>();for(var p:r.products())if(requested.put(p.productId(),p.revision())!=null)throw fail("Виріб повторюється у списку");
  if(requested.isEmpty() || requested.size()>100)throw fail("Оберіть від 1 до 100 виробів");
  var initial=db.queryForList(PRODUCTS,order);var contexts=new HashMap<Long,Map<String,Object>>();var customers=new TreeSet<Long>();customers.add(customer);
  for(long id:requested.keySet()){
   var p=initial.stream().filter(row->num(row,"id")==id).findFirst().orElseThrow(()->fail("Виріб не належить замовленню"));
   String reason=CrmIssueRules.blocked(p);if(reason!=null)throw fail(reason);
   var ctx=payment.read(id);contexts.put(id,ctx);if(ctx.get("customerId")!=null)customers.add(num(ctx,"customerId"));
  }
  // Financial lock order: all customers, invoices, order, products. Matches existing settlement.
  for(long id:customers)db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,id);
  if(retry(customer,branch,order,r,a))return Map.of("issued",true,"duplicate",true);
  var sorted=new ArrayList<>(requested.keySet());sorted.sort(Comparator.comparingLong(id->contexts.get(id).get("invoiceId")==null?0:num(contexts.get(id),"invoiceId")));
  var invoiceLocks=new HashMap<Long,Long>();for(long id:sorted)invoiceLocks.put(id,payment.lock(id));
  var header=order(customer,branch,order,true);
  if(Set.of(5L,10L).contains(num(header,"status")))throw fail("Скасоване замовлення не можна видати");
  var locked=db.queryForList(PRODUCTS+" FOR UPDATE",order);var snapshots=new HashMap<Long,Map<String,Object>>();
  for(long id:requested.keySet()){
   var p=locked.stream().filter(row->num(row,"id")==id).findFirst().orElseThrow(()->fail("Склад замовлення змінився"));
   if(num(p,"revision")!=requested.get(id))throw fail("Стан виробу змінився. Оновіть дані видачі");
   String reason=CrmIssueRules.blocked(p);if(reason!=null)throw fail(reason);
   var current=payment.read(id);
   if(Boolean.TRUE.equals(current.get("externalSettlement")) && !r.externalPaymentChecked())throw fail("Підтвердіть перевірку розрахунку клієнта з посередником");
   snapshots.put(id,payment.checkIssue(id,invoiceLocks.get(id)));
  }
  String note=Objects.requireNonNullElse(r.note(),"");
  for(long id:requested.keySet()){
   int changed=db.update("UPDATE order_products SET current_location='CUSTOMER',pending_location=NULL,transfer_actor=NULL,tracking_revision=tracking_revision+1 WHERE id=? AND tracking_revision=?",id,requested.get(id));
   if(changed!=1)throw fail("Стан виробу змінився. Оновіть дані");
   db.update("INSERT INTO product_transfers(product_id,action,from_location,to_location,actor,note) VALUES(?,'ACCEPT','BRANCH','CUSTOMER',?,?)",id,a.getName(),note);
   payment.recordIssue(id,requested.get(id)+1,true,a.getName(),snapshots.get(id));
  }
  boolean allIssued=locked.stream().allMatch(p->requested.containsKey(num(p,"id")) || "CUSTOMER".equals(p.get("location")));
  if(allIssued)db.update("UPDATE orders SET StatusOrder=12,ClientReceivedDate=CURRENT_TIMESTAMP(6) WHERE OrderNo=?",order);
  String eventNote="Видано виробів: "+requested.size()+". Номери: "+requested.keySet()+". "+note;
  if(eventNote.length()>2000)eventNote=eventNote.substring(0,2000);
  db.update("INSERT INTO crm_contacts(request_key,request_json,customer_id,branch_no,outcome,note,order_no,actor) VALUES(?,?,?,?,'PICKUP_ISSUED',?,?,?)",r.requestKey(),encode(r),customer,branch,eventNote,order,a.getName());
  return Map.of("issued",true,"duplicate",false,"allIssued",allIssued,"productIds",requested.keySet());
 }
 private boolean retry(long customer,long branch,long order,Issue r,Authentication a){
  var rows=db.queryForList("SELECT customer_id,branch_no,order_no,outcome,actor,request_json FROM crm_contacts WHERE request_key=?",r.requestKey());if(rows.isEmpty())return false;
  var old=rows.get(0);
  try{if(num(old,"customer_id")!=customer || num(old,"branch_no")!=branch || old.get("order_no")==null || num(old,"order_no")!=order || !"PICKUP_ISSUED".equals(old.get("outcome")) || !a.getName().equals(old.get("actor")) || !json.readTree(old.get("request_json").toString()).equals(json.readTree(encode(r))))throw fail("Ключ уже використаний для іншої операції");}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}return true;
 }
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
 private static long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private static PreviewException fail(String message){return new PreviewException(HttpStatus.CONFLICT,"CRM_ISSUE_CONFLICT",message);}
}
