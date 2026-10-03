package com.example.baget.billing;

import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.ledger.*;
import com.example.baget.finance.PaymentOrchestrator;
import com.example.baget.branch.BranchRepository;
import com.example.baget.users.User;
import com.example.baget.util.InvoiceServiceUtil;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.baget.billing.BillingAccess.conflict;

@Service @RequiredArgsConstructor
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ProductInvoiceProcessor {
 private final JdbcTemplate db;
 private final BillingAccess access;
 private final InvoiceRepository invoices;
 private final CustomerRepository customers;
 private final BranchRepository branches;
 private final CustomerTransactionRepository transactions;
 private final LedgerRepository ledger;
 private final PaymentOrchestrator orchestrator;
 private final InvoiceServiceUtil numbering;
 private final EntityManager em;
 private final ObjectMapper json;
 private final com.example.baget.finance.InvoiceSettlementService settlement;
 public record Selected(@Positive long productId,@Positive long confirmedRevision){}
 public record Issue(@NotBlank @Size(max=36) String requestKey,@Positive long payerId,@Positive long branchNo,
     @NotEmpty @Size(max=200) List<@NotNull @Valid Selected> products,@Size(max=1000) String note){}
 public record Merge(@NotBlank @Size(max=36) String requestKey,@Positive long payerId,@Positive long branchNo,
     @Size(min=2,max=50) @NotNull List<@NotNull @Positive Long> invoiceIds,@Size(max=1000) String note){}
 private String encode(Object r){try{return json.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException(e);}}
 private JsonNode decode(String r){try{return json.readTree(r);}catch(Exception e){throw new IllegalStateException(e);}}
 private Long begin(String key,String kind,Object r,User user){
  try{UUID.fromString(key);}catch(Exception e){throw conflict("Некоректний ключ запиту");}
  String payload=encode(Map.of("operation",kind,"request",r));
  var prior=db.queryForList("SELECT actor,payload,invoice_id FROM billing_requests WHERE request_key=? FOR UPDATE",key);
  if(!prior.isEmpty()){
   var p=prior.get(0);
   if(!user.getUsername().equals(p.get("actor")) || !decode(payload).equals(decode(p.get("payload").toString())))throw conflict("Ключ уже використано для іншого запиту");
   if(p.get("invoice_id")==null)throw conflict("Запит ще виконується");
   return ((Number)p.get("invoice_id")).longValue();
  }
  db.update("INSERT INTO billing_requests(request_key,actor,payload) VALUES(?,?,?)",key,user.getUsername(),payload);return null;
 }
 private void finish(String key,long id){db.update("UPDATE billing_requests SET invoice_id=? WHERE request_key=?",id,key);}
 public List<Map<String,Object>> ready(int page,Authentication a){return ready(page,null,a);}
 public List<Map<String,Object>> ready(int page,Long partner,Authentication a){
  var u=access.user(a);var args=new ArrayList<Object>();String scope=scope(u,args,"o.BranchNo");if(partner!=null){scope+=" AND o.commerce_mode='RESELLER' AND o.commerce_partner_id=?";args.add(partner);}args.add(offset(page));
  var rows=db.queryForList("SELECT p.id AS productId,p.order_no AS orderNo,p.client_product_id AS productName,p.actual_total AS amount,p.actual_total AS retailAmount,o.commerce_mode AS commerceMode,o.commerce_partner_id AS partnerId,p.confirmed_calculation AS confirmation,p.confirmed_revision AS confirmedRevision,c.Company AS customerName,c.CustNo AS customerId,b.BranchName AS branchName,b.BranchNo AS branchNo FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN customer c ON c.CustNo=o.CustNo JOIN branches b ON b.BranchNo=o.BranchNo WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.warehouse_received_at IS NOT NULL AND p.current_location<>'WORKSHOP' AND (p.pending_location IS NULL OR p.pending_location<>'WORKSHOP') AND p.active_rework_id IS NULL AND p.confirmed_calculation IS NOT NULL AND p.actual_total>0 AND NOT EXISTS(SELECT 1 FROM product_invoice_claims x WHERE x.product_id=p.id) AND NOT EXISTS(SELECT 1 FROM invoice_orders io WHERE io.order_no=p.order_no AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id))"+scope+" ORDER BY p.id LIMIT 100 OFFSET ?",args.toArray());
  for(var row:rows){
   Object raw=row.remove("confirmation");
   if("RESELLER".equals(row.get("commerceMode"))){
    try {var priced=resellerSnapshot(((Number)row.get("productId")).longValue(),decode(raw.toString()));row.put("amount",priced.path("resellerTotal").decimalValue());}
    catch(com.example.baget.calculation.preview.PreviewException ex){row.put("amount",null);row.put("billingError",ex.getMessage());}
   }
  }
  return rows;
 }
 private int offset(int page){return Math.max(0,Math.min(page,100000))*100;}
 private String scope(User u,List<Object> args,String column){
  if(access.admin(u))return "";
  var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();
  if(ids.isEmpty())return " AND 1=0";
  args.addAll(ids);return " AND "+column+" IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
 }
 public Map<String,Object> options(String q,Authentication a){
  String query=q==null?"":q.trim();
  var u=access.user(a);var args=new ArrayList<Object>();String filter=scope(u,args,"BranchNo");
  return Map.of("branches",db.queryForList("SELECT BranchNo AS id,BranchName AS name,(SELECT mode FROM branch_commercial_settings s WHERE s.branch_no=branches.BranchNo) AS mode FROM branches WHERE 1=1 "+filter+" ORDER BY BranchName",args.toArray()),
    "payers",query.length()<2?List.of():db.queryForList("SELECT CustNo AS id,Company AS name,Mobile AS phone FROM customer WHERE LOCATE(?,Company)>0 OR LOCATE(?,Mobile)>0 ORDER BY Company LIMIT 30",query,query),
    "partners",partners(u));
 }
 private List<Map<String,Object>> partners(User u){
  var args=new ArrayList<Object>();String filter=scope(u,args,"o.BranchNo");
  return db.queryForList("SELECT DISTINCT c.CustNo AS id,c.Company AS name FROM orders o JOIN customer c ON c.CustNo=o.commerce_partner_id WHERE o.commerce_mode='RESELLER'"+filter+" ORDER BY c.Company",args.toArray());
 }
 private void ownAccountingBranch(long branch){
  if(!"OWN".equals(db.queryForObject("SELECT mode FROM branch_commercial_settings WHERE branch_no=?",String.class,branch)))throw conflict("Для рахунку посереднику оберіть власну філію обліку підприємства");
 }
 private Long invoicePartner(long invoice){
  var owners=db.queryForList("SELECT partner_id FROM invoice_commercial_owner WHERE invoice_id=?",Long.class,invoice);
  return owners.isEmpty()?null:owners.get(0);
 }
 private com.fasterxml.jackson.databind.node.ObjectNode resellerSnapshot(long pid,JsonNode confirmed){
  return ResellerInvoiceSnapshot.build(confirmed,db.queryForList("SELECT ItemNo AS itemNo,PartNo AS partNo,reseller_unit_price AS price FROM items WHERE order_product_id=? ORDER BY ItemNo",pid));
 }
 public List<Map<String,Object>> list(int page,Authentication a){
  var u=access.user(a);var args=new ArrayList<Object>();String filter="";
  if(!access.admin(u)){
   var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();if(ids.isEmpty())return List.of();
   String marks=String.join(",",Collections.nCopies(ids.size(),"?"));args.addAll(ids);args.addAll(ids);
   filter=" AND NOT EXISTS(SELECT 1 FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=i.id AND o.BranchNo NOT IN ("+marks+")) AND NOT EXISTS(SELECT 1 FROM customer_transactions t WHERE t.invoice_id=i.id AND t.type IN ('INVOICE','INVOICE_MERGE_OUT') AND t.branch_id NOT IN ("+marks+"))";
  }
  args.add(offset(page));
  var rows=db.queryForList("SELECT DISTINCT i.id,i.invoice_no AS invoiceNo,c.Company AS payerName,i.total_amount AS amount,i.status,i.lifecycle,(SELECT partner_id FROM invoice_commercial_owner co WHERE co.invoice_id=i.id) AS partnerId FROM invoices i JOIN customer c ON c.CustNo=i.customer_id WHERE EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=i.id)"+filter+" ORDER BY i.id DESC LIMIT 100 OFFSET ?",args.toArray());
  for(var row:rows)row.put("debt",ledger.calculateInvoiceDebt(((Number)row.get("id")).longValue()));return rows;
 }
 public Map<String,Object> read(long id,Authentication a){
  var u=access.user(a);access.invoice(u,id);
  var header=db.queryForMap("SELECT i.id,i.invoice_no AS invoiceNo,i.total_amount AS amount,i.status,i.lifecycle,i.note,i.customer_id AS payerId,i.created_at AS createdAt,c.Company AS payerName FROM invoices i JOIN customer c ON c.CustNo=i.customer_id WHERE i.id=?",id);
  var lines=db.queryForList("SELECT ip.product_id AS productId,ip.order_no AS orderNo,ip.confirmed_revision AS confirmedRevision,ip.original_amount AS originalAmount,ip.billed_amount AS amount,ip.source_invoice_id AS sourceInvoiceId,s.invoice_no AS sourceInvoiceNo,ip.snapshot FROM invoice_products ip LEFT JOIN invoices s ON s.id=ip.source_invoice_id WHERE ip.invoice_id=? ORDER BY ip.product_id",id);
  if(lines.isEmpty())throw conflict("Це рахунок старого формату");
  for(var l:lines)l.put("snapshot",decode(l.get("snapshot").toString()));
  header.put("history",db.queryForList("SELECT type,amount,note,created_at AS createdAt FROM customer_transactions WHERE invoice_id=? AND active=1 ORDER BY id",id));
  header.put("mergedInto",db.queryForList("SELECT DISTINCT i.id,i.invoice_no AS invoiceNo FROM invoice_products ip JOIN invoices i ON i.id=ip.invoice_id WHERE ip.source_invoice_id=?",id));
  header.put("partnerId",invoicePartner(id));
  header.put("products",lines);header.put("debt",ledger.calculateInvoiceDebt(id));header.put("availableAdvance",transactions.calculateAvailableAdvance(((Number)header.get("payerId")).longValue()));return header;
 }
 public Map<String,Object> issue(Issue r,Authentication a){
  var user=access.user(a);access.branch(user,r.branchNo());
  var previous=begin(r.requestKey(),"ISSUE",r,user);if(previous!=null)return read(previous,a);
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,r.payerId());
  var ids=new TreeSet<Long>();for(var p:r.products())if(!ids.add(p.productId()))throw conflict("Дубль виробу");
  var orderIds=new TreeSet<Long>();
  for(long id:ids){var rows=db.queryForList("SELECT order_no FROM order_products WHERE id=? AND active=1",Long.class,id);if(rows.isEmpty())throw conflict("Виріб не знайдено");orderIds.add(rows.get(0));}
  for(long id:orderIds)db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id);
  var entries=new ArrayList<Map<String,Object>>();
  BigDecimal total=BigDecimal.ZERO;Boolean resellerBatch=null;Long partnerId=null;
  for(var selected:r.products()){
   var p=db.queryForMap("SELECT p.*,o.BranchNo AS branchNo FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=? FOR UPDATE",selected.productId());
   access.branch(user,((Number)p.get("branchNo")).longValue());
   boolean reseller="RESELLER".equals(db.queryForObject("SELECT commerce_mode FROM orders WHERE OrderNo=?",String.class,p.get("order_no")));
   if(resellerBatch!=null && resellerBatch!=reseller)throw conflict("Власні та реселлерські вироби оформлюються окремими рахунками");
   resellerBatch=reseller;
   if(reseller){
    Long owner=db.queryForObject("SELECT commerce_partner_id FROM orders WHERE OrderNo=?",Long.class,p.get("order_no"));
    if(owner==null || owner.longValue()!=r.payerId() || (partnerId!=null && !partnerId.equals(owner)))throw conflict("Усі вироби мають належати обраній фірмі-посереднику");
    partnerId=owner;
   }
   if(db.queryForObject("SELECT COUNT(*) FROM orders WHERE OrderNo=? AND StatusOrder IN (5,10)",Long.class,p.get("order_no"))>0)throw conflict("Замовлення скасоване");
   if(!Boolean.TRUE.equals(p.get("active")) && !(p.get("active") instanceof Number n && n.intValue()==1))throw conflict("Виріб уже неактивний");
   if(!Boolean.TRUE.equals(p.get("production_confirmed")) && !(p.get("production_confirmed") instanceof Number confirmed && confirmed.intValue()==1))throw conflict("Витрати виробу не підтверджено");
   if(p.get("active_rework_id")!=null || "WORKSHOP".equals(p.get("current_location")) || "WORKSHOP".equals(p.get("pending_location")))throw conflict("Виріб повертається до майстерні або перебуває на доопрацюванні");
   if(!"COMPLETED".equals(p.get("production_state")) || p.get("warehouse_received_at")==null || p.get("confirmed_calculation")==null || p.get("confirmed_revision")==null || ((Number)p.get("confirmed_revision")).longValue()!=selected.confirmedRevision())throw conflict("Виріб ще не прийнятий на склад або розрахунок змінився");
   if(db.queryForObject("SELECT COUNT(*) FROM product_invoice_claims WHERE product_id=?",Long.class,selected.productId())>0)throw conflict("Виріб уже включений у рахунок");
   if(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,p.get("order_no"))>0)throw conflict("Замовлення вже має рахунок старого формату");
   var amount=(BigDecimal)p.get("actual_total");if(amount==null||amount.signum()<=0)throw conflict("Некоректна підтверджена сума");
   var snapshot=decode(p.get("confirmed_calculation").toString());
   if(snapshot.path("total").decimalValue().compareTo(amount)!=0 || snapshot.path("productId").asLong()!=selected.productId() || snapshot.path("revision").asLong()!=selected.confirmedRevision())throw conflict("Підтверджений знімок не відповідає виробу");
   if(reseller){
    var priced=resellerSnapshot(selected.productId(),snapshot);priced.put("partnerId",partnerId);
    amount=priced.path("resellerTotal").decimalValue();
    if(amount.signum()<=0)throw conflict("Реселлерська сума має бути додатною");
    p.put("confirmed_calculation",encode(priced));
   }
   p.put("invoiceAmount",amount);total=total.add(amount);entries.add(p);
  }
  if(partnerId!=null)ownAccountingBranch(r.branchNo());
  Invoice invoice=create(r.payerId(),total,r.note());
  if(partnerId!=null)db.update("INSERT INTO invoice_commercial_owner(invoice_id,partner_id) VALUES(?,?)",invoice.getId(),partnerId);
  for(var p:entries){
   db.update("INSERT INTO invoice_products(invoice_id,product_id,order_no,confirmed_revision,snapshot,original_amount,billed_amount) VALUES(?,?,?,?,?,?,?)",invoice.getId(),p.get("id"),p.get("order_no"),p.get("confirmed_revision"),p.get("confirmed_calculation"),p.get("invoiceAmount"),p.get("invoiceAmount"));
   db.update("INSERT INTO product_invoice_claims(product_id,invoice_id) VALUES(?,?)",p.get("id"),invoice.getId());
  }
  linkOrders(invoice.getId());
  var tx=transaction(invoice,r.branchNo(),CustomerTransactionType.INVOICE,total.negate(),r.note());
  orchestrator.postInvoiceTransactions(List.of(tx),user,invoice.getCustomer());
  em.flush();finish(r.requestKey(),invoice.getId());return read(invoice.getId(),a);
 }
 private Invoice create(long payerId,BigDecimal total,String note){
  var payer=customers.findById(payerId).orElseThrow(()->conflict("Платника не знайдено"));
  // A product invoice is consolidated even for one product; old per-order payment mutations must not run.
  var invoice=Invoice.builder().invoiceNo(numbering.generateTodayCode()).customer(payer).payer(payer).type(InvoiceEnums.InvoiceType.CONSOLIDATED).status(InvoiceEnums.InvoiceStatus.ISSUED).lifecycle(InvoiceEnums.InvoiceLifecycle.ACTIVE).totalAmount(total).note(note).build();
  invoices.saveAndFlush(invoice);return invoice;
 }
 private void linkOrders(long id){db.update("INSERT INTO invoice_orders(invoice_id,order_no,amount) SELECT invoice_id,order_no,SUM(billed_amount) FROM invoice_products WHERE invoice_id=? GROUP BY invoice_id,order_no",id);}
 private CustomerTransaction transaction(Invoice invoice,long branchId,CustomerTransactionType type,BigDecimal amount,String note){
  var branch=branches.findById(branchId).orElseThrow(()->conflict("Філію не знайдено"));
  // No order_no: one order may have several invoices, and legacy CT order uniqueness must remain intact.
  return transactions.saveAndFlush(CustomerTransaction.builder().customer(invoice.getCustomer()).invoice(invoice).branch(branch).type(type).amount(amount).createdAt(OffsetDateTime.now()).note(note).build());
 }
 public record Payment(@NotBlank @Size(max=36) String requestKey,
     @NotNull @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal amount,
     @NotNull @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal allocationAmount,
     @NotNull @Positive @Digits(integer=15,fraction=2) BigDecimal expectedDebt,@Size(max=1000) String note){}
 public Map<String,Object> pay(long id,Payment r,Authentication a){
  var user=access.user(a);access.invoice(user,id);
  if(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,id)==0)throw conflict("Це рахунок старого формату");
  var previous=begin(r.requestKey(),"PAYMENT",Map.of("invoiceId",id,"payment",r),user);if(previous!=null)return read(previous,a);
  long customer=db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,id);
  db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
  db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,id);
  var debt=ledger.calculateInvoiceDebt(id);
  if(debt.compareTo(r.expectedDebt())!=0)throw conflict("Борг змінився. Оновіть рахунок перед оплатою");
  if(r.amount().add(r.allocationAmount()).signum()<=0 || r.amount().add(r.allocationAmount()).compareTo(debt)>0)throw conflict("Сума оплати та авансу повинна бути додатною й не перевищувати борг");
  settlement.settleInvoice(new InvoicePaymentRequest(null,id,null,r.amount(),r.allocationAmount(),r.note()),a);
  em.flush();finish(r.requestKey(),id);return read(id,a);
 }
 public Map<String,Object> merge(Merge r,Authentication a){
  var user=access.user(a);access.branch(user,r.branchNo());
  var previous=begin(r.requestKey(),"MERGE",r,user);if(previous!=null)return read(previous,a);
  var ids=new TreeSet<>(r.invoiceIds());if(ids.size()!=r.invoiceIds().size())throw conflict("Дубль рахунку");
  var customersToLock=new TreeSet<Long>();customersToLock.add(r.payerId());
  for(long id:ids){access.invoice(user,id);customersToLock.add(db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,id));}
  for(long id:customersToLock)db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,id);
  for(long id:ids)db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,id);
  var orders=new TreeSet<Long>();for(long id:ids)orders.addAll(db.queryForList("SELECT order_no FROM invoice_orders WHERE invoice_id=?",Long.class,id));
  for(long id:orders)db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id);
  Boolean resellerMerge=null;Long mergePartner=null;
  var sources=new ArrayList<Invoice>();var debts=new HashMap<Long,BigDecimal>();BigDecimal total=BigDecimal.ZERO;
  for(long id:ids){
   Long owner=invoicePartner(id);boolean reseller=owner!=null;
   if(resellerMerge!=null && resellerMerge!=reseller)throw conflict("Не можна змішувати власні й реселлерські рахунки");
   resellerMerge=reseller;
   if(owner!=null){if(owner.longValue()!=r.payerId())throw conflict("Не можна переносити борг посередника іншому платнику");mergePartner=owner;}
   var inv=invoices.findById(id).orElseThrow();em.refresh(inv);
   if(owner!=null && !owner.equals(inv.getCustomer().getCustNo()))throw conflict("Платник рахунку не відповідає фірмі");
   if(inv.getLifecycle()!=InvoiceEnums.InvoiceLifecycle.ACTIVE || inv.getStatus()==InvoiceEnums.InvoiceStatus.CANCELLED)throw conflict("Рахунок уже об'єднаний або скасований");
   if(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,id)==0)throw conflict("Старі рахунки об'єднуються через попередній інтерфейс; змішування форматів заборонене");
   BigDecimal debt=ledger.calculateInvoiceDebt(id);
   if(debt.stripTrailingZeros().scale()>2 || debt.signum()<=0||debt.compareTo(inv.getTotalAmount())>0)throw conflict("Рахунок не має додатного узгодженого боргу");
   sources.add(inv);debts.put(id,debt);total=total.add(debt);
  }
  if(mergePartner!=null)ownAccountingBranch(r.branchNo());
  var target=create(r.payerId(),total,r.note());
  if(mergePartner!=null)db.update("INSERT INTO invoice_commercial_owner(invoice_id,partner_id) VALUES(?,?)",target.getId(),mergePartner);
  var postings=new ArrayList<CustomerTransaction>();
  for(var source:sources){
   var rows=db.queryForList("SELECT * FROM invoice_products WHERE invoice_id=? ORDER BY product_id",source.getId());
   var amounts=rows.stream().map(row->(BigDecimal)row.get("billed_amount")).toList();
   if(amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(source.getTotalAmount())!=0)throw conflict("Склад рахунку не узгоджений із сумою");
   var remaining=BillingMath.remaining(amounts,debts.get(source.getId()));
   for(int i=0;i<rows.size();i++){
    var row=rows.get(i);
    if(db.update("UPDATE product_invoice_claims SET invoice_id=? WHERE product_id=? AND invoice_id=?",target.getId(),row.get("product_id"),source.getId())!=1)throw conflict("Виріб уже належить іншому чинному рахунку");
    db.update("INSERT INTO invoice_products(invoice_id,product_id,order_no,confirmed_revision,snapshot,original_amount,billed_amount,source_invoice_id) VALUES(?,?,?,?,?,?,?,?)",target.getId(),row.get("product_id"),row.get("order_no"),row.get("confirmed_revision"),row.get("snapshot"),row.get("original_amount"),remaining.get(i),source.getId());
   }
   var owner=ledger.findTopByInvoiceIdAndDirectionAndCategoryInOrderByCreatedAtDescIdDesc(source.getId(),LedgerDirection.OUT,List.of(LedgerCategory.INVOICE_ISSUED,LedgerCategory.INVOICE_MERGE_OUT)).orElseThrow(()->conflict("Не визначена філія рахунку"));
   postings.add(transaction(source,owner.getBranch().getBranchNo(),CustomerTransactionType.INVOICE_MERGE_IN,debts.get(source.getId()),"Перенесення залишку в рахунок "+target.getInvoiceNo()));
   source.setLifecycle(InvoiceEnums.InvoiceLifecycle.MERGED);invoices.save(source);
  }
  linkOrders(target.getId());
  postings.add(transaction(target,r.branchNo(),CustomerTransactionType.INVOICE_MERGE_OUT,total.negate(),"Об'єднання залишків рахунків "+ids));
  orchestrator.postInvoiceTransactions(postings,user,target.getCustomer());em.flush();finish(r.requestKey(),target.getId());return read(target.getId(),a);
 }
}
