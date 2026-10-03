package com.example.baget.partnerpayments;
import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.users.User;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PartnerPaymentService {
 private final JdbcTemplate db;
 private final CustomerPaymentAccess access;
 private final ObjectMapper json;
 public PartnerPaymentService(JdbcTemplate db,CustomerPaymentAccess access,ObjectMapper json){this.db=db;this.access=access;this.json=json;}
 public Map<String,Object> context(Long order,Long customer,Long branch,Authentication a){
  var u=access.user(a);Map<String,Object> row;
  if(order!=null){
   row=db.queryForMap("SELECT CustNo AS customerId,BranchNo AS branchNo,commerce_mode AS mode,commerce_partner_id AS partnerId FROM orders WHERE OrderNo=?",order);
   access.branch(u,num(row,"branchNo"));
   if(customer!=null && customer.longValue()!=num(row,"customerId"))throw conflict("Замовлення належить іншому клієнту");
   if(branch!=null && branch.longValue()!=num(row,"branchNo"))throw conflict("Змінилася філія замовлення");
   row.put("contextOrder",order);
  }else{
   if(customer==null || branch==null)throw conflict("Оберіть клієнта та філію");
   access.customer(u,customer,branch);
   row=db.queryForMap("SELECT b.BranchNo AS branchNo,s.mode,s.partner_id AS partnerId FROM branches b LEFT JOIN branch_commercial_settings s ON s.branch_no=b.BranchNo WHERE b.BranchNo=? FOR SHARE",branch);
   row.put("customerId",customer);
  }
  if(row.get("mode")==null)row.put("mode","OWN");
  // A company paying our invoice is an enterprise customer, not its own retail customer.
  if(row.get("partnerId")!=null && num(row,"partnerId")==num(row,"customerId")){row.put("mode","OWN");row.put("partnerId",null);}
  if("RESELLER".equals(row.get("mode"))){
   if(row.get("partnerId")==null)throw conflict("Не налаштовано фірму-посередника");
   row.put("partnerName",db.queryForObject("SELECT Company FROM customer WHERE CustNo=?",String.class,num(row,"partnerId")));
  }
  return row;
 }
 private Map<String,Object> account(long customer,long branch,long partner,Long order,Authentication a){
  var ctx=context(order,customer,branch,a);
  if(!"RESELLER".equals(ctx.get("mode")) || ctx.get("partnerId")==null || num(ctx,"partnerId")!=partner)
   throw conflict("Фірма або режим обліку змінилися. Відкрийте фінанси повторно");
  return ctx;
 }
 private String scope(User u,List<Object> args,String column){
  if(access.admin(u))return "";
  var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();
  if(ids.isEmpty())return " AND 1=0";
  args.addAll(ids);return " AND "+column+" IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
 }
 private BigDecimal available(long customer,long partner){
  return db.queryForObject("SELECT COALESCE(SUM(advance_delta),0) FROM partner_customer_movements WHERE customer_id=? AND partner_id=?",BigDecimal.class,customer,partner);
 }
 private static final String ORDERS="""
 SELECT o.OrderNo AS orderNo,o.BranchNo AS branchNo,b.BranchName AS branchName,o.StatusOrder AS status,
 (SELECT SUM(CASE WHEN p.production_confirmed=1 THEN p.actual_total WHEN ap.state IN ('AUTO','APPROVED') THEN ap.current_retail ELSE p.total END)
  FROM order_products p LEFT JOIN product_price_approval ap ON ap.product_id=p.id WHERE p.order_no=o.OrderNo AND p.active=1) AS total,
 (SELECT COUNT(*) FROM order_products p LEFT JOIN product_price_approval ap ON ap.product_id=p.id WHERE p.order_no=o.OrderNo AND p.active=1 AND ap.state IN ('WAITING','REJECTED')) AS pricePending,
 (SELECT COUNT(*) FROM order_products p WHERE p.order_no=o.OrderNo AND p.active=1 AND p.production_confirmed=0) AS unfinished,
 (SELECT COUNT(*) FROM order_products p LEFT JOIN product_price_approval ap ON ap.product_id=p.id WHERE p.order_no=o.OrderNo AND p.active=1 AND (CASE WHEN p.production_confirmed=1 THEN p.actual_total WHEN ap.state IN ('AUTO','APPROVED') THEN ap.current_retail ELSE p.total END) IS NULL) AS incomplete,
 COALESCE((SELECT SUM(t.paid_delta) FROM partner_customer_movements t WHERE t.order_no=o.OrderNo AND t.partner_id=o.commerce_partner_id AND t.customer_id=o.CustNo),0) AS paid
 FROM orders o JOIN branches b ON b.BranchNo=o.BranchNo
 WHERE o.CustNo=? AND o.commerce_partner_id=? AND o.commerce_mode='RESELLER'
 """;
 private void decorate(Map<String,Object> row){
  BigDecimal total=decimal(row,"total"),paid=decimal(row,"paid");
  row.put("debt",total==null?null:total.subtract(paid).max(BigDecimal.ZERO));
  row.put("overpayment",total==null?BigDecimal.ZERO:paid.subtract(total).max(BigDecimal.ZERO));
  row.put("canTransfer",total!=null && paid.compareTo(total)>0 && num(row,"unfinished")==0 && num(row,"pricePending")==0 && num(row,"incomplete")==0 && !Set.of(5L,10L).contains(num(row,"status")));
  row.put("canPay",total!=null && total.compareTo(paid)>0 && num(row,"pricePending")==0 && num(row,"incomplete")==0 && !Set.of(5L,10L).contains(num(row,"status")));
 }
 public Map<String,Object> read(long customer,long branch,long partner,Long contextOrder,int page,int historyPage,Authentication a){
  var ctx=account(customer,branch,partner,contextOrder,a);var u=access.user(a);
  var args=new ArrayList<Object>(List.of(customer,partner));String filter=scope(u,args,"o.BranchNo");args.add(offset(page));
  var rows=db.queryForList(ORDERS+filter+" ORDER BY o.OrderNo DESC LIMIT 50 OFFSET ?",args.toArray());rows.forEach(this::decorate);
  // The opened order is shown even when it is not on the first page.
  Object selected=null;
  if(contextOrder!=null){var selectedRows=db.queryForList(ORDERS+" AND o.OrderNo=?",customer,partner,contextOrder);if(!selectedRows.isEmpty()){decorate(selectedRows.get(0));selected=selectedRows.get(0);}}
  var historyArgs=new ArrayList<Object>(List.of(customer,partner));String historyScope=scope(u,historyArgs,"m.branch_no");historyArgs.add(offset(historyPage));
  var history=db.queryForList("SELECT m.id,m.entry_type AS entryType,m.operation,m.cash_delta AS cashDelta,m.advance_delta AS advanceDelta,m.paid_delta AS paidDelta,m.source_payment_id AS sourcePaymentId,m.order_no AS orderNo,m.branch_no AS branchNo,m.actor,m.note,m.created_at AS createdAt,EXISTS(SELECT 1 FROM partner_payment_adjustments r WHERE m.entry_type='PAYMENT' AND r.source_payment_id=m.id) AS reversed FROM partner_customer_movements m WHERE m.customer_id=? AND m.partner_id=?"+historyScope+" ORDER BY m.created_at DESC,m.entry_type DESC,m.id DESC LIMIT 50 OFFSET ?",historyArgs.toArray());
  var result=new HashMap<String,Object>(ctx);result.put("customerName",db.queryForObject("SELECT Company FROM customer WHERE CustNo=?",String.class,customer));
  result.put("canAdjust",canAdjust(u));result.put("availableAdvance",available(customer,partner));result.put("orders",rows);result.put("selectedOrder",selected);result.put("history",history);return result;
 }
 public record Payment(@NotBlank @Size(max=36) String requestKey,@Positive long branchNo,@Positive long partnerId,
  @Positive Long contextOrder,@Positive Long orderNo,
  @NotNull @DecimalMin("0") @Digits(integer=9,fraction=2) BigDecimal amount,
  @NotNull @DecimalMin("0") @Digits(integer=9,fraction=2) BigDecimal allocationAmount,
  @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal expectedTotal,
  @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal expectedPaid,
  @Size(max=2000) String note) {}
 public JsonNode pay(long customer,Payment r,Authentication a){
  var u=access.user(a);account(customer,r.branchNo(),r.partnerId(),r.contextOrder(),a);
  try{if(!UUID.fromString(r.requestKey()).toString().equals(r.requestKey()))throw new IllegalArgumentException();}catch(IllegalArgumentException ex){throw conflict("Некоректний ключ операції");}
  // Same customer lock order as existing payments; serializes allocations at all reseller branches.
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
  var old=db.queryForList("SELECT customer_id,partner_id,actor,payload,result FROM partner_customer_payments WHERE request_key=?",r.requestKey());
  if(!old.isEmpty()){
   var prior=old.get(0);
   if(num(prior,"customer_id")!=customer || num(prior,"partner_id")!=r.partnerId() || !a.getName().equals(prior.get("actor")) || !same(r,parse(prior.get("payload").toString())))throw conflict("Ключ уже використаний для іншої операції");
   return tree(prior.get("result").toString());
  }
  BigDecimal balance=available(customer,r.partnerId()),debt=null;
  if(r.orderNo()!=null){
   db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,r.orderNo());
   var rows=db.queryForList(ORDERS+" AND o.OrderNo=?",customer,r.partnerId(),r.orderNo());
   if(rows.isEmpty())throw conflict("Замовлення не належить цьому клієнту та фірмі");
   var order=rows.get(0);access.branch(u,num(order,"branchNo"));decorate(order);
   if(!Boolean.TRUE.equals(order.get("canPay")))throw conflict("Замовлення скасоване, вже оплачене або очікує погодження ціни");
   if(r.expectedTotal()==null || r.expectedPaid()==null || r.expectedTotal().compareTo(decimal(order,"total"))!=0 || r.expectedPaid().compareTo(decimal(order,"paid"))!=0)throw conflict("Ціна або оплата змінилися. Оновіть дані");
   debt=decimal(order,"debt");
  }
  try{PartnerPaymentRules.validate(r.amount(),r.allocationAmount(),balance,debt);}catch(IllegalArgumentException ex){throw conflict(ex.getMessage());}
  var result=new LinkedHashMap<String,Object>();result.put("requestKey",r.requestKey());result.put("partnerId",r.partnerId());result.put("customerId",customer);result.put("orderNo",r.orderNo());
  result.put("availableAdvance",r.orderNo()==null?balance.add(r.amount()):balance.subtract(r.allocationAmount()));
  result.put("remainingDebt",debt==null?null:debt.subtract(r.amount()).subtract(r.allocationAmount()));
  db.update("INSERT INTO partner_customer_payments(request_key,partner_id,customer_id,branch_no,order_no,operation,amount,allocation_amount,actor,note,payload,result) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",r.requestKey(),r.partnerId(),customer,r.branchNo(),r.orderNo(),r.orderNo()==null?"ADVANCE":"PAYMENT",r.amount(),r.allocationAmount(),a.getName(),r.note(),encode(r),encode(result));
  return json.valueToTree(result);
 }
 private boolean canAdjust(User u){return u.getRoles()!=null && u.getRoles().stream().anyMatch(r->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER").contains(r.getName()));}
 public record Adjustment(@NotBlank @Size(max=36) String requestKey,@Positive long branchNo,@Positive long partnerId,
  @Positive Long contextOrder,@NotNull AdjustmentKind operation,@Positive Long sourcePaymentId,@Positive Long orderNo,
  @NotNull @DecimalMin("0") @Digits(integer=9,fraction=2) BigDecimal amount,
  @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal expectedTotal,
  @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal expectedPaid,
  @NotBlank @Size(max=2000) String note) {}
 public enum AdjustmentKind { REVERSAL, TRANSFER }
 public JsonNode adjust(long customer,Adjustment r,Authentication a){
  var u=access.user(a);
  if(!canAdjust(u))throw new PreviewException(HttpStatus.FORBIDDEN,"PARTNER_ADJUSTMENT_ACCESS","Коригувати кошти може бухгалтер або адміністратор");
  account(customer,r.branchNo(),r.partnerId(),r.contextOrder(),a);
  try{if(!UUID.fromString(r.requestKey()).toString().equals(r.requestKey()))throw new IllegalArgumentException();}catch(IllegalArgumentException ex){throw conflict("Некоректний ключ операції");}
  if(r.note()==null || r.note().isBlank() || r.note().length()>2000)throw conflict("Вкажіть причину коригування");
  if(r.operation()==null)throw conflict("Оберіть вид коригування");
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
  var prior=db.queryForList("SELECT customer_id,partner_id,actor,payload,result FROM partner_payment_adjustments WHERE request_key=?",r.requestKey());
  if(!prior.isEmpty()){
   var old=prior.get(0);
   if(num(old,"customer_id")!=customer || num(old,"partner_id")!=r.partnerId() || !a.getName().equals(old.get("actor")) || !sameAdjustment(r,parseAdjustment(old.get("payload").toString())))throw conflict("Ключ уже використаний для іншої операції");
   return tree(old.get("result").toString());
  }
  BigDecimal balance=available(customer,r.partnerId());
  PartnerPaymentRules.Deltas delta;Long targetOrder;long eventBranch;
  try{
   if(r.operation()==AdjustmentKind.REVERSAL){
    if(r.sourcePaymentId()==null || r.orderNo()!=null || r.amount()==null || r.amount().signum()!=0)throw conflict("Оберіть початковий платіж для повного скасування");
    var payments=db.queryForList("SELECT id,branch_no,order_no,operation,amount,allocation_amount FROM partner_customer_payments WHERE id=? AND customer_id=? AND partner_id=?",r.sourcePaymentId(),customer,r.partnerId());
    if(payments.isEmpty())throw conflict("Платіж не належить цьому клієнту та фірмі");
    var payment=payments.get(0);eventBranch=num(payment,"branch_no");access.branch(u,eventBranch);
    if(!db.queryForList("SELECT id FROM partner_payment_adjustments WHERE source_payment_id=?",r.sourcePaymentId()).isEmpty())throw conflict("Цей платіж уже скасовано");
    targetOrder=payment.get("order_no")==null?null:num(payment,"order_no");
    BigDecimal paid=null;
    if(targetOrder!=null){var order=lockedOrder(customer,r.partnerId(),targetOrder,u);paid=decimal(order,"paid");}
    delta=PartnerPaymentRules.reverse("ADVANCE".equals(payment.get("operation")),decimal(payment,"amount"),decimal(payment,"allocation_amount"),balance,paid);
   }else{
    if(r.sourcePaymentId()!=null || r.orderNo()==null)throw conflict("Оберіть замовлення з переплатою");
    targetOrder=r.orderNo();var order=lockedOrder(customer,r.partnerId(),targetOrder,u);eventBranch=num(order,"branchNo");decorate(order);
    if(!Boolean.TRUE.equals(order.get("canTransfer")))throw conflict("Перенесення можливе для завершеного, нескасованого замовлення з погодженою ціною та переплатою");
    if(!eq(r.expectedTotal(),decimal(order,"total")) || !eq(r.expectedPaid(),decimal(order,"paid")))throw conflict("Ціна або оплата змінилися. Оновіть дані");
    delta=PartnerPaymentRules.transfer(r.amount(),decimal(order,"total"),decimal(order,"paid"));
   }
  }catch(IllegalArgumentException ex){throw conflict(ex.getMessage());}
  var result=new LinkedHashMap<String,Object>();result.put("requestKey",r.requestKey());result.put("operation",r.operation());result.put("customerId",customer);result.put("partnerId",r.partnerId());result.put("orderNo",targetOrder);
  result.put("cashDelta",delta.cash());result.put("advanceDelta",delta.advance());result.put("paidDelta",delta.paid());result.put("availableAdvance",balance.add(delta.advance()));
  db.update("INSERT INTO partner_payment_adjustments(request_key,partner_id,customer_id,branch_no,order_no,operation,source_payment_id,cash_delta,advance_delta,paid_delta,actor,note,payload,result) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",r.requestKey(),r.partnerId(),customer,eventBranch,targetOrder,r.operation().name(),r.sourcePaymentId(),delta.cash(),delta.advance(),delta.paid(),a.getName(),r.note().trim(),encode(r),encode(result));
  return json.valueToTree(result);
 }
 private Map<String,Object> lockedOrder(long customer,long partner,long order,User u){
  db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,order);
  var rows=db.queryForList(ORDERS+" AND o.OrderNo=?",customer,partner,order);
  if(rows.isEmpty())throw conflict("Замовлення не належить цьому клієнту та фірмі");
  var row=rows.get(0);access.branch(u,num(row,"branchNo"));return row;
 }
 static boolean sameAdjustment(Adjustment a,Adjustment b){return a.requestKey().equals(b.requestKey()) && a.branchNo()==b.branchNo() && a.partnerId()==b.partnerId() && Objects.equals(a.contextOrder(),b.contextOrder()) && a.operation()==b.operation() && Objects.equals(a.sourcePaymentId(),b.sourcePaymentId()) && Objects.equals(a.orderNo(),b.orderNo()) && eq(a.amount(),b.amount()) && eq(a.expectedTotal(),b.expectedTotal()) && eq(a.expectedPaid(),b.expectedPaid()) && Objects.equals(a.note(),b.note());}
 private Adjustment parseAdjustment(String v){try{return json.readValue(v,Adjustment.class);}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean same(Payment a,Payment b){return a.requestKey().equals(b.requestKey()) && a.branchNo()==b.branchNo() && a.partnerId()==b.partnerId() && Objects.equals(a.contextOrder(),b.contextOrder()) && Objects.equals(a.orderNo(),b.orderNo()) && eq(a.amount(),b.amount()) && eq(a.allocationAmount(),b.allocationAmount()) && eq(a.expectedTotal(),b.expectedTotal()) && eq(a.expectedPaid(),b.expectedPaid()) && Objects.equals(a.note(),b.note());}
 private static boolean eq(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null && a.compareTo(b)==0;}
 private Payment parse(String v){try{return json.readValue(v,Payment.class);}catch(Exception e){throw new IllegalStateException(e);}}
 private JsonNode tree(String v){try{return json.readTree(v);}catch(Exception e){throw new IllegalStateException(e);}}
 private String encode(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
 private static int offset(int page){return Math.max(0,Math.min(page,100000))*50;}
 private static long num(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 private static BigDecimal decimal(Map<String,Object> r,String k){return r.get(k)==null?null:new BigDecimal(r.get(k).toString());}
 private static PreviewException conflict(String m){return new PreviewException(HttpStatus.CONFLICT,"PARTNER_PAYMENT_CONFLICT",m);}
}
