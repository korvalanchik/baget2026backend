package com.example.baget.finance;
import com.example.baget.billing.BillingAccess;
import com.example.baget.invoices.*;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CustomerCreditService {
 private final JdbcTemplate db; private final CustomerPaymentAccess access; private final BillingAccess billing;
 private final InvoiceRepository invoices; private final InvoiceFinanceService finance; private final ObjectMapper json;
 public CustomerCreditService(JdbcTemplate db,CustomerPaymentAccess access,BillingAccess billing,InvoiceRepository invoices,InvoiceFinanceService finance,ObjectMapper json) {
  this.db=db;this.access=access;this.billing=billing;this.invoices=invoices;this.finance=finance;this.json=json;
 }
 public Map<String,Object> policy(long customer,long branch) {
  var rows=db.queryForList("SELECT allow_unpaid AS allowed,revision FROM customer_credit_policy WHERE customer_id=? AND branch_no=?",customer,branch);
  return rows.isEmpty()?Map.of("allowed",false,"revision",0L):rows.get(0);
 }
 public Map<String,Object> terms(long invoice) {
  var rows=db.queryForList("SELECT due_date AS dueDate,revision FROM invoice_payment_terms WHERE invoice_id=?",invoice);
  if(!rows.isEmpty())return rows.get(0);
  var result=new HashMap<String,Object>();result.put("dueDate",null);result.put("revision",0L);return result;
 }
 static boolean enabled(Object value){return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1;}
 static LocalDate date(Object value){return value==null?null:LocalDate.parse(value.toString());}
 private boolean editor(Authentication a) {
  var u=access.user(a);
  return u.getRoles().stream().anyMatch(x->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER").contains(x.getName()));
 }
 public Map<String,Object> read(long customer,long branch,Authentication a) {
  access.customer(access.user(a),customer,branch);
  var result=new HashMap<String,Object>();result.put("policy",policy(customer,branch));result.put("canEdit",editor(a));
  var rows=new ArrayList<Map<String,Object>>();
  for(long id:db.queryForList("SELECT DISTINCT i.id FROM invoices i JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no WHERE i.customer_id=? AND o.BranchNo=? AND i.lifecycle='ACTIVE' AND i.status NOT IN ('CANCELLED','DRAFT') ORDER BY i.id DESC",Long.class,customer,branch)) {
   var invoice=invoices.findById(id).orElseThrow();var debt=finance.calculateInvoiceDebt(invoice);
   if(debt.signum()>0) {var row=new HashMap<String,Object>(terms(id));row.put("invoiceId",id);row.put("invoiceNo",invoice.getInvoiceNo());row.put("debt",debt);row.put("overdue",CreditRules.overdue(debt,date(row.get("dueDate")),today()));rows.add(row);}
  }
  result.put("invoices",rows);
  result.put("events",db.queryForList("SELECT action,invoice_id AS invoiceId,actor,payload,created_at AS createdAt FROM customer_credit_events WHERE customer_id=? AND branch_no=? ORDER BY id DESC LIMIT 100",customer,branch));
  result.put("issues",db.queryForList("SELECT product_id AS productId,invoice_id AS invoiceId,action,debt,due_date AS dueDate,actor,created_at AS createdAt FROM product_credit_issues WHERE customer_id=? AND branch_no=? ORDER BY id DESC LIMIT 100",customer,branch));
  return result;
 }
 public record Policy(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long expectedRevision,boolean allowed,@NotBlank @Size(max=1000) String note) {}
 public record Terms(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long expectedRevision,LocalDate dueDate,@NotBlank @Size(max=1000) String note) {}
 public Map<String,Object> savePolicy(long customer,long branch,Policy r,Authentication a) {
  var u=billing.user(a);access.customer(u,customer,branch);
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
  if(retry(customer,branch,null,"POLICY",r.requestKey(),r,a)) return read(customer,branch,a);
  var old=policy(customer,branch);if(((Number)old.get("revision")).longValue()!=r.expectedRevision())throw conflict("Дозвіл змінився. Оновіть картку");
  db.update("INSERT INTO customer_credit_policy(customer_id,branch_no,allow_unpaid,revision) VALUES(?,?,?,1) ON DUPLICATE KEY UPDATE allow_unpaid=VALUES(allow_unpaid),revision=revision+1",customer,branch,r.allowed());
  event(customer,branch,null,"POLICY",r.requestKey(),r,a);return read(customer,branch,a);
 }
 public Map<String,Object> saveTerms(long customer,long branch,long invoiceId,Terms r,Authentication a) {
  var u=billing.user(a);access.customer(u,customer,branch);billing.invoice(u,invoiceId);
  if(r.dueDate()!=null && (r.dueDate().getYear()<1000 || r.dueDate().getYear()>9999))throw conflict("Некоректна дата");
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
  db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,invoiceId);
  var header=db.queryForMap("SELECT customer_id,lifecycle,status FROM invoices WHERE id=?",invoiceId);
  if(((Number)header.get("customer_id")).longValue()!=customer)throw conflict("Рахунок належить іншому клієнту");
  if(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=? AND o.BranchNo=?",Long.class,invoiceId,branch)==0)throw conflict("Рахунок не належить цій філії");
  if(retry(customer,branch,invoiceId,"TERMS",r.requestKey(),r,a))return read(customer,branch,a);
  if(!"ACTIVE".equals(header.get("lifecycle")) || Set.of("CANCELLED","DRAFT").contains(header.get("status")))throw conflict("Рахунок нечинний");
  if(((Number)terms(invoiceId).get("revision")).longValue()!=r.expectedRevision())throw conflict("Строк оплати змінився. Оновіть картку");
  db.update("INSERT INTO invoice_payment_terms(invoice_id,due_date,revision) VALUES(?,?,1) ON DUPLICATE KEY UPDATE due_date=VALUES(due_date),revision=revision+1",invoiceId,r.dueDate());
  event(customer,branch,invoiceId,"TERMS",r.requestKey(),r,a);return read(customer,branch,a);
 }
 private boolean retry(long customer,long branch,Long invoice,String action,String key,Object body,Authentication a) {
  try{if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw conflict("Некоректний ключ операції");}
  var rows=db.queryForList("SELECT customer_id,branch_no,invoice_id,action,actor,payload FROM customer_credit_events WHERE request_key=?",key);
  if(rows.isEmpty())return false;var old=rows.get(0);
  Long oldInvoice=old.get("invoice_id")==null?null:((Number)old.get("invoice_id")).longValue();
  try {
   if(((Number)old.get("customer_id")).longValue()!=customer || ((Number)old.get("branch_no")).longValue()!=branch || !Objects.equals(invoice,oldInvoice) || !action.equals(old.get("action")) || !a.getName().equals(old.get("actor")) || !json.readTree(old.get("payload").toString()).equals(json.readTree(encode(body))))throw conflict("Ключ уже використано для іншої операції");
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}return true;
 }
 private void event(long customer,long branch,Long invoice,String action,String key,Object body,Authentication a) {
  db.update("INSERT INTO customer_credit_events(request_key,customer_id,branch_no,invoice_id,action,actor,payload) VALUES(?,?,?,?,?,?,?)",key,customer,branch,invoice,action,a.getName(),encode(body));
 }
 private String encode(Object v){try{return json.writeValueAsString(v);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 static LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Kyiv"));}
 private static PreviewException conflict(String message){return new PreviewException(HttpStatus.CONFLICT,"CREDIT_CONFLICT",message);}
 public Object receivables(Long branch,boolean overdue,int page,Authentication a) {
  var u=access.user(a);if(page<0 || page>100000)throw conflict("Некоректна сторінка");
  var branches=new TreeSet<Long>();for (var allowedBranch : u.getAllowedBranches()) {branches.add(allowedBranch.getBranchNo());}
  if(access.admin(u))branches.addAll(db.queryForList("SELECT BranchNo FROM branches",Long.class));
  if(branch!=null){access.branch(u,branch);branches=new TreeSet<>(Set.of(branch));}
  var result=new ArrayList<Map<String,Object>>();
  // Filter current debts before pagination; never infer payment from invoice status or orders.AmountDueN.
  for(long branchId:branches)for(var row:db.queryForList("SELECT DISTINCT i.id AS invoiceId,i.invoice_no AS invoiceNo,i.customer_id AS customerId,c.Company AS company,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS mobile,b.BranchNo AS branchNo,b.BranchName AS branchName FROM invoices i JOIN customer c ON c.CustNo=i.customer_id JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE b.BranchNo=? AND i.lifecycle='ACTIVE' AND i.status NOT IN ('CANCELLED','DRAFT') ORDER BY i.id",branchId)) {
   long id=((Number)row.get("invoiceId")).longValue();var invoice=invoices.findById(id).orElseThrow();var debt=finance.calculateInvoiceDebt(invoice);var terms=terms(id);boolean late=CreditRules.overdue(debt,date(terms.get("dueDate")),today());
   if(debt.signum()>0 && (!overdue || late)){row.put("debt",debt);row.put("dueDate",terms.get("dueDate"));row.put("overdue",late);result.add(row);}
  }
  result.sort(Comparator.comparing((Map<String,Object> x)->date(x.get("dueDate")),Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(x->((Number)x.get("invoiceId")).longValue()).thenComparingLong(x->((Number)x.get("branchNo")).longValue()));
  int start=Math.min(result.size(),page*50),end=Math.min(result.size(),start+50);return Map.of("rows",result.subList(start,end),"hasMore",end<result.size());
 }
}
