package com.example.baget.cash;
import com.example.baget.users.User;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ReceiptPaymentsService {
 private final JdbcTemplate db;private final CashService cash;private final ObjectMapper json;
 public ReceiptPaymentsService(JdbcTemplate db,CashService cash,ObjectMapper json){this.db=db;this.cash=cash;this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);}
 private User editor(Authentication a){var u=cash.user(a);if(!CashService.editor(u))throw CashService.denied();return u;}
 private Map<String,Object> receipt(long id,boolean lock){
  var rows=db.queryForList("SELECT r.id,r.document_no AS documentNo,r.document_date AS documentDate,r.snapshot,v.VendorName AS vendorName FROM priced_material_receipts r JOIN vendors v ON v.VendorNo=r.vendor_no WHERE r.id=?"+(lock?" FOR UPDATE":""),id);
  if(rows.isEmpty())throw CashService.bad("Проведений прихід не знайдено");return rows.get(0);
 }
 private BigDecimal value(JsonNode n,String field){var v=n.get(field);if(v==null||!v.isNumber())throw CashService.bad("У знімку приходу відсутня сума: "+field);return v.decimalValue();}
 private Map<String,BigDecimal> totals(Map<String,Object> receipt){
  try{var s=json.readTree(receipt.get("snapshot").toString());var doc=s.get("document");if(doc==null||!s.path("lines").isArray())throw CashService.bad("Неповний знімок проведеного приходу");
   var totals=new LinkedHashMap<String,BigDecimal>();totals.put("MATERIALS",value(s,"purchaseTotal"));totals.put("DELIVERY",value(doc,"deliveryCost"));totals.put("PROCUREMENT",value(doc,"procurementCost"));BigDecimal direct=BigDecimal.ZERO;for(var line:s.get("lines"))direct=direct.add(value(line,"directCost"));totals.put("DIRECT",direct);
   if(totals.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(value(s,"total"))!=0)throw CashService.bad("Суми знімка приходу не збігаються");return totals;
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw CashService.bad("Не вдалося прочитати знімок приходу");}
 }
 private List<Map<String,Object>> payments(long id){return db.queryForList("SELECT p.id,p.cash_account_id AS accountId,a.name AS accountName,p.component,p.kind,l.amount,l.direction,p.payee,p.document_ref AS documentRef,p.note,p.actor,p.reversal_of AS reversalOf,p.created_at AS createdAt FROM receipt_payments p LEFT JOIN ledger_entries l ON l.receipt_payment_id=p.id JOIN cash_accounts a ON a.id=p.cash_account_id WHERE p.receipt_id=? ORDER BY p.id DESC",id);}
 private long revision(List<Map<String,Object>> rows){return rows.stream().mapToLong(r->CashService.num(r,"id")).max().orElse(0);}
 private List<ReceiptPaymentMath.Due> dues(Map<String,Object> r,List<Map<String,Object>> rows){try{return ReceiptPaymentMath.dues(totals(r),rows);}catch(IllegalArgumentException e){throw CashService.bad(e.getMessage());}}
 private List<Map<String,Object>> accounts(User u){return db.queryForList("SELECT a.id,a.name,a.kind,a.branch_no AS branchNo,COALESCE((SELECT SUM(CASE WHEN l.direction='IN' THEN l.amount ELSE -l.amount END) FROM ledger_entries l WHERE l.cash_account_id=a.id),0) AS balance FROM cash_accounts a JOIN branch_commercial_settings s ON s.branch_no=a.branch_no AND s.mode='OWN' WHERE a.initialized=1 AND a.kind IN ('BRANCH_CASH','CENTRAL_CASH','BANK') ORDER BY a.id").stream().filter(r->!"BRANCH_CASH".equals(r.get("kind"))||CashService.role(u,"ROLE_ADMIN")||u.getAllowedBranches().stream().anyMatch(b->b.getBranchNo()==CashService.num(r,"branchNo"))).toList();}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object list(String q,int page,Authentication a){editor(a);q=q==null?"":q.trim();if(q.length()>120||page<0||page>100000)throw CashService.bad("Некоректний пошук або сторінка");var rows=db.queryForList("SELECT r.id,r.document_no AS documentNo,r.document_date AS documentDate,v.VendorName AS vendorName FROM priced_material_receipts r JOIN vendors v ON v.VendorNo=r.vendor_no WHERE (?='' OR LOCATE(?,r.document_no)>0 OR LOCATE(?,v.VendorName)>0 OR CAST(r.id AS CHAR)=?) ORDER BY r.id DESC LIMIT 51 OFFSET ?",q,q,q,q,page*50);return Map.of("rows",rows.subList(0,Math.min(50,rows.size())),"hasMore",rows.size()>50);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object read(long id,Authentication a){var u=editor(a);var r=receipt(id,false);var rows=payments(id).stream().map(row->(Map<String,Object>)new LinkedHashMap<String,Object>(row)).toList();var info=new LinkedHashMap<>(r);info.remove("snapshot");var accounts=accounts(u);var allowed=accounts.stream().map(rw->CashService.num(rw,"id")).collect(java.util.stream.Collectors.toSet());var reversed=rows.stream().filter(rw->rw.get("reversalOf")!=null).map(rw->CashService.num(rw,"reversalOf")).collect(java.util.stream.Collectors.toSet());for(var row:rows)row.put("canReverse","PAYMENT".equals(row.get("kind"))&&!reversed.contains(CashService.num(row,"id"))&&allowed.contains(CashService.num(row,"accountId")));return Map.of("receipt",info,"dues",dues(r,rows),"payments",rows,"accounts",accounts,"revision",revision(rows));}
 public record Pay(@NotBlank String requestKey,@Positive long accountId,@NotBlank String component,
  @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal amount,
  @Min(0) long expectedRevision,@NotBlank @Size(max=255) String payee,@Size(max=120) String documentRef,@NotBlank @Size(max=1000) String note){}
 public Object pay(long id,Pay r,Authentication a){
  var u=editor(a);cash.serialize();var acc=cash.account(r.accountId());cash.view(u,acc);
  var payload=Map.of("receiptId",id,"payment",r);var prior=cash.prior("RECEIPT_PAYMENT",r.requestKey(),payload,u);if(prior!=null)return prior;
  cash.ready(acc);if("TRANSIT".equals(acc.get("kind")))throw CashService.bad("Кошти в дорозі не можна витрачати");
  var document=receipt(id,true);var rows=payments(id);
  try{ReceiptPaymentMath.validate(dues(document,rows),r.component(),r.amount(),r.expectedRevision(),revision(rows));}catch(IllegalArgumentException e){throw CashService.bad(e.getMessage());}
  cash.enough(r.accountId(),r.amount());
  db.update("INSERT INTO receipt_payments(receipt_id,cash_account_id,component,kind,amount,payee,document_ref,note,actor) VALUES(?,?,?,'PAYMENT',?,?,?,?,?)",id,r.accountId(),r.component(),r.amount(),r.payee().trim(),r.documentRef()==null?"":r.documentRef().trim(),r.note().trim(),u.getUsername());
  long paymentId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);post(acc,u,paymentId,id,r.component(),"OUT",r.amount(),r.payee(),r.note());
  return cash.remember("RECEIPT_PAYMENT",r.requestKey(),payload,Map.of("id",paymentId,"receiptId",id,"message","Оплату проведено. Собівартість і залишки матеріалів не змінено"),u);
 }
 public record Reverse(@NotBlank String requestKey,@Positive long paymentId,@Min(0) long expectedRevision,@NotBlank @Size(max=1000) String note){}
 public Object reverse(long id,Reverse r,Authentication a){
  var u=editor(a);cash.serialize();var document=receipt(id,true);var rows=payments(id);var original=rows.stream().filter(p->CashService.num(p,"id")==r.paymentId()&&"PAYMENT".equals(p.get("kind"))).findFirst().orElseThrow(()->CashService.bad("Оплату цього приходу не знайдено"));
  var acc=cash.account(CashService.num(original,"accountId"));cash.view(u,acc);
  var payload=Map.of("receiptId",id,"reversal",r);var prior=cash.prior("RECEIPT_REVERSAL",r.requestKey(),payload,u);if(prior!=null)return prior;
  cash.ready(acc);if(revision(rows)!=r.expectedRevision())throw CashService.bad("Історія оплат змінилася. Оновіть документ");
  if(rows.stream().anyMatch(p->p.get("reversalOf")!=null&&CashService.num(p,"reversalOf")==r.paymentId()))throw CashService.bad("Цю оплату вже сторновано");
  dues(document,rows);var amount=CashService.decimal(original,"amount");String component=original.get("component").toString(),payee=original.get("payee").toString();
  db.update("INSERT INTO receipt_payments(receipt_id,cash_account_id,component,kind,amount,payee,document_ref,note,actor,reversal_of) VALUES(?,?,?,'REVERSAL',?,?,?,?,?,?)",id,acc.get("id"),component,amount,payee,original.get("documentRef"),r.note().trim(),u.getUsername(),r.paymentId());
  long paymentId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);post(acc,u,paymentId,id,component,"IN",amount,payee,"Сторно оплати №"+r.paymentId()+": "+r.note());
  return cash.remember("RECEIPT_REVERSAL",r.requestKey(),payload,Map.of("id",paymentId,"receiptId",id,"message","Помилкову оплату сторновано в тій самій касі / на рахунку"),u);
 }
 private void post(Map<String,Object> acc,User u,long payment,long receipt,String component,String direction,BigDecimal amount,String payee,String note){
  String category=switch(component){case "MATERIALS"->"MATERIAL_PURCHASE";case "DELIVERY"->"PURCHASE_DELIVERY";case "PROCUREMENT"->"PURCHASE_PROCUREMENT";case "DIRECT"->"PURCHASE_DIRECT";default->throw CashService.bad("Невідома складова закупівлі");};
  db.update("INSERT INTO ledger_entries(branch_id,direction,category,amount,created_at,created_by,cash_account_id,receipt_payment_id,reference,note) VALUES(?,?,?,?,CURRENT_TIMESTAMP(6),?,?,?,?,?)",acc.get("branch_no"),direction,category,amount,u.getId(),acc.get("id"),payment,"RECEIPT-"+receipt,payee+" · "+note);
 }
}
