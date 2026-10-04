package com.example.baget.crm;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CrmService {
 private final JdbcTemplate db;
 private final NamedParameterJdbcTemplate named;
 private final UsersRepository users;
 private final ObjectMapper json;
 public CrmService(JdbcTemplate db,UsersRepository users,ObjectMapper json) {
  this.db=db;this.named=new NamedParameterJdbcTemplate(db);this.users=users;this.json=json;
 }
 private Set<Long> access(Authentication a) {
  if(a==null||!a.isAuthenticated())throw fail(HttpStatus.UNAUTHORIZED,"Увійдіть до системи");
  var u=users.findByUsername(a.getName()).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"Немає доступу до CRM"));
  var roles=u.getRoles().stream().map(r->r.getName()).toList();
  if(Collections.disjoint(roles,Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_FOREMAN")))
   throw fail(HttpStatus.FORBIDDEN,"Немає доступу до CRM");
  var branches=new TreeSet<Long>();u.getAllowedBranches().forEach(b->branches.add(b.getBranchNo()));
  if(branches.isEmpty())throw fail(HttpStatus.FORBIDDEN,"Немає дозволених філій");
  return branches;
 }
 public List<Map<String,Object>> branches(Authentication a) {
  return named.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches WHERE BranchNo IN (:branches) ORDER BY BranchName",Map.of("branches",access(a)));
 }
 private static final String RELATIONS = """
 SELECT customer_id AS customerId,branch_id AS branchNo FROM customer_transactions WHERE active=1
 UNION
 SELECT customer_id,branch_no FROM partner_customer_movements
 UNION
 SELECT CustNo AS customerId,BranchNo AS branchNo FROM orders
 UNION
 SELECT COALESCE(i.payer_id,i.customer_id),o.BranchNo FROM invoices i
 JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no
 UNION
 SELECT i.customer_id,o.BranchNo FROM invoices i
 JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no
 """;
 private static final String ORDER_STATE = """
 SELECT o.OrderNo,o.CustNo,o.BranchNo,
 CASE WHEN o.StatusOrder IN (5,10) THEN 0
      WHEN COALESCE(p.total,0)>0 THEN COALESCE(p.working,0)
      WHEN o.StatusOrder IN (0,1,2) THEN 1 ELSE 0 END AS working,
 CASE WHEN o.StatusOrder IN (5,10) THEN 0
      WHEN COALESCE(p.total,0)>0 THEN COALESCE(p.ready,0)
      WHEN o.StatusOrder IN (3,4,6,7,8,9,11) THEN 1 ELSE 0 END AS ready
 FROM orders o LEFT JOIN (
  SELECT order_no,COUNT(*) total,
  SUM(CASE WHEN production_state NOT IN ('COMPLETED','STOPPED') AND current_location<>'CUSTOMER' THEN 1 ELSE 0 END) working,
  SUM(CASE WHEN production_state='COMPLETED' AND current_location<>'CUSTOMER' THEN 1 ELSE 0 END) ready
  FROM order_products WHERE active=1 GROUP BY order_no
 ) p ON p.order_no=o.OrderNo
 """;
 public Object list(Long branch,String tab,String q,boolean all,boolean due,int page,Authentication a) {
  // Global StringTrimmerEditor(true) converts empty query parameters to null.
  q=Objects.toString(q,"").trim();
  tab=tab==null||tab.isBlank()?"WORK":tab.trim();
  if("PARTNER".equals(tab))tab="SETTLEMENT"; // Old clients no longer filter the retired manual flag.
  boolean settlement=Set.of("SETTLEMENT","DEBT","ADVANCE","OVERDUE").contains(tab);
  Set<Long> allowed=access(a);
  if(settlement && users.findByUsername(a.getName()).orElseThrow().getRoles().stream().noneMatch(role->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2").contains(role.getName())))
   throw fail(HttpStatus.FORBIDDEN,"Немає доступу до розрахунків клієнтів");
  if(branch!=null){if(!allowed.contains(branch))throw fail(HttpStatus.FORBIDDEN,"Цю філію не дозволено");allowed=Set.of(branch);}
  if(!Set.of("WORK","READY","SETTLEMENT","DEBT","ADVANCE","OVERDUE").contains(tab)||page<0||page>100000||q.length()>100)
   throw fail(HttpStatus.BAD_REQUEST,"Некоректні параметри пошуку");
  String condition=settlement?CrmSettlementSql.condition(tab):all?"1=1":"READY".equals(tab)?"COALESCE(s.readyOrders,0)>0":"COALESCE(s.workingOrders,0)>0";
  String sql="""
   SELECT c.CustNo AS customerId,c.Company AS company,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS mobile,
   r.branchNo,b.BranchName AS branchName,COALESCE(cp.is_partner,0) AS partner,
   COALESCE(cp.revision,0) AS revision,cp.next_contact_date AS nextContactDate,
   COALESCE(s.workingOrders,0) AS workingOrders,COALESCE(s.readyOrders,0) AS readyOrders,
   h.note AS lastNote,h.outcome AS lastOutcome,h.created_at AS lastContact,h.actor AS lastActor
   """+(settlement?CrmSettlementSql.COLUMNS:"")+"""
   FROM (
   """+RELATIONS+"""
   ) r JOIN customer c ON c.CustNo=r.customerId JOIN branches b ON b.BranchNo=r.branchNo
   LEFT JOIN crm_customer_profiles cp ON cp.customer_id=r.customerId AND cp.branch_no=r.branchNo
   LEFT JOIN (
    SELECT CustNo,BranchNo,SUM(CASE WHEN working>0 THEN 1 ELSE 0 END) AS workingOrders,
    SUM(CASE WHEN ready>0 THEN 1 ELSE 0 END) AS readyOrders FROM (
   """+ORDER_STATE+"""
    ) os GROUP BY CustNo,BranchNo
   ) s ON s.CustNo=r.customerId AND s.BranchNo=r.branchNo
   LEFT JOIN crm_contacts h ON h.id=(SELECT MAX(x.id) FROM crm_contacts x WHERE x.customer_id=r.customerId AND x.branch_no=r.branchNo AND x.outcome<>'PARTNER')
   WHERE r.branchNo IN (:branches) AND (
   """+condition+"""
   ) AND (:q='' OR LOCATE(:q,c.Company)>0 OR LOCATE(:q,COALESCE(c.Mobile,''))>0
    OR LOCATE(:q,COALESCE(c.Phone,''))>0
    OR (:digits<>'' AND (LOCATE(:digits,REGEXP_REPLACE(COALESCE(c.Mobile,''),'[^0-9]',''))>0
     OR LOCATE(:digits,REGEXP_REPLACE(COALESCE(c.Phone,''),'[^0-9]',''))>0))
    OR EXISTS(SELECT 1 FROM orders so WHERE so.CustNo=r.customerId AND so.BranchNo=r.branchNo AND CAST(so.OrderNo AS CHAR)=:q)
    OR EXISTS(SELECT 1 FROM invoices si JOIN invoice_orders sio ON sio.invoice_id=si.id
     JOIN orders so ON so.OrderNo=sio.order_no WHERE si.payer_id=r.customerId AND so.BranchNo=r.branchNo AND CAST(so.OrderNo AS CHAR)=:q))
   AND (:due=0 OR cp.next_contact_date<=:today)
   ORDER BY cp.next_contact_date IS NULL,cp.next_contact_date,c.Company,c.CustNo,r.branchNo LIMIT 51 OFFSET :offset
   """;
  var args=new HashMap<String,Object>();args.put("branches",allowed);args.put("q",q.trim());args.put("digits",q.matches("[+()\\s0-9-]+")?q.replaceAll("[^0-9]",""):"");
  args.put("due",due?1:0);args.put("today",LocalDate.now(java.time.ZoneId.of("Europe/Kyiv")));args.put("offset",page*50);
  var rows=named.queryForList(sql,args);
  var visible=rows.subList(0,Math.min(50,rows.size()));
  for(var row:visible)row.put("pickupOrders",pickupOrders(((Number)row.get("customerId")).longValue(),((Number)row.get("branchNo")).longValue()));
  return Map.of("rows",visible,"hasMore",rows.size()>50);
 }
 void customer(long id,long branch,Authentication a) {
  if(!access(a).contains(branch))throw fail(HttpStatus.FORBIDDEN,"Цю філію не дозволено");
  Long n=db.queryForObject("SELECT COUNT(*) FROM ("+RELATIONS+") r WHERE r.customerId=? AND r.branchNo=?",Long.class,id,branch);
  if(n==null||n==0)throw fail(HttpStatus.NOT_FOUND,"Клієнт не має замовлень або рахунків у цій філії");
 }
 public Object detail(long id,long branch,int page,Authentication a) {
  customer(id,branch,a);if(page<0||page>100000)throw fail(HttpStatus.BAD_REQUEST,"Некоректна сторінка");
  var out=new LinkedHashMap<String,Object>();
  out.put("profile",profile(id,branch));
  out.put("pickupOrders",pickupOrders(id,branch));
  out.put("orders",db.queryForList("""
   SELECT o.OrderNo AS orderNo,o.StatusOrder AS status,o.SaleDate AS saleDate,o.commerce_mode AS commerceMode,pc.Company AS partnerName,
   p.id AS productId,p.client_product_id AS productName,p.production_state AS state,
   p.current_location AS location,p.pending_location AS pending
   FROM orders o LEFT JOIN order_products p ON p.order_no=o.OrderNo AND p.active=1
   LEFT JOIN customer pc ON pc.CustNo=o.commerce_partner_id
   WHERE o.CustNo=? AND o.BranchNo=? ORDER BY o.OrderNo DESC,p.id LIMIT 201
   """,id,branch));
  out.put("invoices",db.queryForList("""
   SELECT DISTINCT i.id,i.invoice_no AS invoiceNo,i.status,i.lifecycle
   FROM invoices i JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no
   WHERE (i.customer_id=? OR i.payer_id=?) AND o.BranchNo=? ORDER BY i.id DESC LIMIT 201
   """,id,id,branch));
  var events=db.queryForList("""
   SELECT e.id,e.outcome,e.note,e.order_no AS orderNo,e.invoice_id AS invoiceId,i.invoice_no AS invoiceNo,e.next_contact_date AS nextContactDate,e.pickup_date AS pickupDate,
   e.actor,e.created_at AS createdAt FROM crm_contacts e LEFT JOIN invoices i ON i.id=e.invoice_id
   WHERE e.customer_id=? AND e.branch_no=? ORDER BY e.id DESC LIMIT 51 OFFSET ?
   """,id,branch,page*50);
  out.put("history",events.subList(0,Math.min(50,events.size())));out.put("hasMore",events.size()>50);
  return out;
 }
 private Map<String,Object> profile(long id,long branch) {
  var rows=db.queryForList("SELECT is_partner AS partner,revision,next_contact_date AS nextContactDate FROM crm_customer_profiles WHERE customer_id=? AND branch_no=?",id,branch);
  if(!rows.isEmpty())return rows.get(0);
  var result=new HashMap<String,Object>();result.put("partner",false);result.put("revision",0L);result.put("nextContactDate",null);return result;
 }
 public record Contact(@NotBlank @Size(max=36) String requestKey,@Min(0) long expectedRevision,
   @NotBlank String outcome,@NotBlank @Size(max=2000) String note,Long orderNo,Long invoiceId,LocalDate nextContactDate) {}
 public record Partner(@NotBlank @Size(max=36) String requestKey,@Min(0) long expectedRevision,@NotNull Boolean partner) {}
 public Object contact(long id,long branch,Contact r,Authentication a) {
  if(!Set.of("NOTIFIED","NO_ANSWER","CALL_BACK","AGREEMENT","NOTE","DONE").contains(r.outcome()))
   throw fail(HttpStatus.BAD_REQUEST,"Невідомий результат контакту");
  if("CALL_BACK".equals(r.outcome())&&r.nextContactDate()==null)throw fail(HttpStatus.BAD_REQUEST,"Вкажіть дату наступного дзвінка");
  if("DONE".equals(r.outcome())&&r.nextContactDate()!=null)throw fail(HttpStatus.BAD_REQUEST,"Для завершеної домовленості очистіть наступну дату");
  if(r.nextContactDate()!=null&&(r.nextContactDate().getYear()<1000||r.nextContactDate().getYear()>9999))
   throw fail(HttpStatus.BAD_REQUEST,"Некоректна дата наступного контакту");
  customer(id,branch,a);lock(id);
  if(retry(r.requestKey(),r,id,branch,a))return detail(id,branch,0,a);
  checkRevision(id,branch,r.expectedRevision());
  if(r.orderNo()!=null&&db.queryForObject("SELECT COUNT(*) FROM orders WHERE OrderNo=? AND CustNo=? AND BranchNo=?",Long.class,r.orderNo(),id,branch)==0)
   throw fail(HttpStatus.BAD_REQUEST,"Замовлення не належить клієнту та філії");
  if(r.invoiceId()!=null&&db.queryForObject("""
   SELECT COUNT(*) FROM invoices i WHERE i.id=? AND (i.customer_id=? OR i.payer_id=?)
   AND EXISTS(SELECT 1 FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=i.id AND o.BranchNo=?)
   """,Long.class,r.invoiceId(),id,id,branch)==0)throw fail(HttpStatus.BAD_REQUEST,"Рахунок не належить клієнту та філії");
  db.update("UPDATE crm_customer_profiles SET next_contact_date=?,revision=revision+1 WHERE customer_id=? AND branch_no=?",r.nextContactDate(),id,branch);
  event(id,branch,r.requestKey(),r,r.outcome(),r.note().trim(),r.orderNo(),r.invoiceId(),r.nextContactDate(),a);
  return detail(id,branch,0,a);
 }
 public Object partner(long id,long branch,Partner r,Authentication a) {
  customer(id,branch,a);lock(id);
  if(retry(r.requestKey(),r,id,branch,a))return detail(id,branch,0,a);
  checkRevision(id,branch,r.expectedRevision());
  db.update("UPDATE crm_customer_profiles SET is_partner=?,revision=revision+1 WHERE customer_id=? AND branch_no=?",r.partner(),id,branch);
  event(id,branch,r.requestKey(),r,"PARTNER",r.partner()?"Позначено як партнера":"Знято ознаку партнера",null,null,null,a);
  return detail(id,branch,0,a);
 }
 private void lock(long id){db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,id);}
 private void checkRevision(long id,long branch,long expected) {
  var current=profile(id,branch);
  if(((Number)current.get("revision")).longValue()!=expected)throw fail(HttpStatus.CONFLICT,"Картку змінено. Оновіть її перед збереженням");
  db.update("INSERT IGNORE INTO crm_customer_profiles(customer_id,branch_no) VALUES(?,?)",id,branch);
 }
 private boolean retry(String key,Object request,long id,long branch,Authentication a) {
  try{if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}
  catch(IllegalArgumentException e){throw fail(HttpStatus.BAD_REQUEST,"Некоректний ключ операції");}
  var rows=db.queryForList("SELECT customer_id,branch_no,actor,request_json FROM crm_contacts WHERE request_key=?",key);
  if(rows.isEmpty())return false;
  var old=rows.get(0);
  try {
   // Parse both sides from JSON so numeric nodes use the same types (IntNode vs LongNode).
   if(((Number)old.get("customer_id")).longValue()!=id||((Number)old.get("branch_no")).longValue()!=branch
     ||!a.getName().equals(old.get("actor"))||!json.readTree(old.get("request_json").toString()).equals(json.readTree(json.writeValueAsString(request))))
    throw fail(HttpStatus.CONFLICT,"Ключ уже використано з іншими даними");
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
  return true;
 }
 private void event(long id,long branch,String key,Object request,String outcome,String note,Long order,Long invoice,LocalDate next,Authentication a) {
  db.update("""
   INSERT INTO crm_contacts(request_key,request_json,customer_id,branch_no,outcome,note,order_no,invoice_id,next_contact_date,actor)
   VALUES(?,?,?,?,?,?,?,?,?,?)
   """,key,json.valueToTree(request).toString(),id,branch,outcome,note,order,invoice,next,a.getName());
 }
 public record Pickup(@NotBlank @Size(max=36) String requestKey,@Min(0) long expectedRevision,
   @NotBlank String readySignature,@NotBlank String outcome,@NotNull @Size(max=2000) String note,LocalDate pickupDate) {}
 public Object pickup(long id,long branch,long order,Pickup r,Authentication a) {
  customer(id,branch,a);lock(id);
  if(retry(r.requestKey(),r,id,branch,a)) {
   var old=db.queryForMap("SELECT order_no FROM crm_contacts WHERE request_key=?",r.requestKey());
   if(((Number)old.get("order_no")).longValue()!=order)throw fail(HttpStatus.CONFLICT,"Ключ використано для іншого замовлення");
   return detail(id,branch,0,a);
  }
  if(!Set.of("PICKUP_NOTIFIED","PICKUP_NO_ANSWER","PICKUP_AGREED","PICKUP_CLEAR").contains(r.outcome()))
   throw fail(HttpStatus.BAD_REQUEST,"Невідома дія видачі");
  if("PICKUP_AGREED".equals(r.outcome())&&r.pickupDate()==null)
   throw fail(HttpStatus.BAD_REQUEST,"Вкажіть обіцяну дату отримання");
  if(!"PICKUP_AGREED".equals(r.outcome())&&r.pickupDate()!=null)
   throw fail(HttpStatus.BAD_REQUEST,"Дата отримання задається лише для домовленості");
  if(r.pickupDate()!=null&&(r.pickupDate().getYear()<1000||r.pickupDate().getYear()>9999))
   throw fail(HttpStatus.BAD_REQUEST,"Некоректна дата");
  var match=db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? AND CustNo=? AND BranchNo=? FOR UPDATE",order,id,branch);
  if(match.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Замовлення не належить клієнту та філії");
  var item=pickupOrders(id,branch).stream().filter(o->((Number)o.get("orderNo")).longValue()==order).findFirst()
    .orElseThrow(()->fail(HttpStatus.CONFLICT,"Готових невиданих виробів уже немає. Оновіть картку"));
  if(((Number)item.get("revision")).longValue()!=r.expectedRevision()||!item.get("readySignature").equals(r.readySignature()))
   throw fail(HttpStatus.CONFLICT,"Готовність або домовленість змінилася. Оновіть картку");
  db.update("INSERT IGNORE INTO crm_order_pickup(order_no) VALUES(?)",order);
  event(id,branch,r.requestKey(),r,r.outcome(),r.note().trim(),order,null,null,a);
  Long eventId=db.queryForObject("SELECT id FROM crm_contacts WHERE request_key=?",Long.class,r.requestKey());
  Object date="PICKUP_AGREED".equals(r.outcome())?r.pickupDate():"PICKUP_CLEAR".equals(r.outcome())?null:item.get("pickupDate");
  db.update("UPDATE crm_contacts SET pickup_date=?,ready_snapshot=? WHERE id=?",date,json.valueToTree(item.get("readyKeys")).toString(),eventId);
  boolean notified=Set.of("PICKUP_NOTIFIED","PICKUP_AGREED").contains(r.outcome());
  db.update("UPDATE crm_order_pickup SET revision=revision+1,pickup_date=?,last_event_id=?,notified_event_id=CASE WHEN ? THEN ? ELSE notified_event_id END WHERE order_no=?",date,eventId,notified,eventId,order);
  return detail(id,branch,0,a);
 }
 List<Map<String,Object>> pickupOrders(long id,long branch) {
  var rows=db.queryForList("""
   SELECT o.OrderNo AS orderNo,o.StatusOrder AS orderStatus,p.id AS productId,p.production_state AS state,
   p.current_location AS location,p.pending_location AS pending,p.completed_at AS completedAt,
   p.production_revision AS productionRevision,p.tracking_revision AS trackingRevision,
   COALESCE(cp.revision,0) AS revision,cp.pickup_date AS pickupDate,
   le.outcome AS lastOutcome,le.ready_snapshot AS lastSnapshot,ne.ready_snapshot AS notifiedSnapshot
   FROM orders o LEFT JOIN order_products p ON p.order_no=o.OrderNo AND p.active=1
   LEFT JOIN crm_order_pickup cp ON cp.order_no=o.OrderNo
   LEFT JOIN crm_contacts le ON le.id=cp.last_event_id
   LEFT JOIN crm_contacts ne ON ne.id=cp.notified_event_id
   WHERE o.CustNo=? AND o.BranchNo=? AND o.StatusOrder NOT IN (5,10)
   AND (EXISTS(SELECT 1 FROM order_products rp WHERE rp.order_no=o.OrderNo AND rp.active=1
     AND rp.production_state='COMPLETED' AND rp.current_location<>'CUSTOMER')
    OR (NOT EXISTS(SELECT 1 FROM order_products ap WHERE ap.order_no=o.OrderNo AND ap.active=1)
     AND o.StatusOrder IN (3,4,6,7,8,9,11)))
   ORDER BY o.OrderNo DESC,p.id
   """,id,branch);
  var grouped=new LinkedHashMap<Long,List<Map<String,Object>>>();
  for(var row:rows)grouped.computeIfAbsent(((Number)row.get("orderNo")).longValue(),k->new ArrayList<>()).add(row);
  var result=new ArrayList<Map<String,Object>>();
  for(var entry:grouped.entrySet()) {
   var first=entry.getValue().get(0);var keys=new TreeSet<String>();var signature=new ArrayList<String>();
   int branchReady=0,working=0;boolean legacy=first.get("productId")==null;
   var places=new TreeSet<String>();
   for(var row:entry.getValue()) {
    if(row.get("productId")==null){keys.add("legacy:"+entry.getKey());places.add("UNKNOWN");signature.add("legacy:"+row.get("orderStatus"));continue;}
    signature.add(row.get("productId")+":"+row.get("state")+":"+row.get("productionRevision")+":"+row.get("trackingRevision"));
    if(!"COMPLETED".equals(row.get("state"))){if(!"STOPPED".equals(row.get("state"))&&!"CUSTOMER".equals(row.get("location")))working++;continue;}
    if("CUSTOMER".equals(row.get("location")))continue;
    // Completion timestamp changes on another production/rework cycle; moving the same product does not.
    keys.add(row.get("productId")+":"+Objects.toString(row.get("completedAt"),"revision:"+row.get("productionRevision")));
    places.add(Objects.toString(row.get("location"),"UNKNOWN")+(row.get("pending")==null?"":" → "+row.get("pending")));
    if("BRANCH".equals(row.get("location"))&&row.get("pending")==null)branchReady++;
   }
   if(keys.isEmpty())continue;
   var notified=snapshot(first.get("notifiedSnapshot"));var last=snapshot(first.get("lastSnapshot"));
   var date=first.get("pickupDate")==null?null:LocalDate.parse(first.get("pickupDate").toString());
   var out=new LinkedHashMap<String,Object>();
   out.put("orderNo",entry.getKey());out.put("revision",first.get("revision"));out.put("readyKeys",keys);
   out.put("readySignature",hashPickup(signature));out.put("readyCount",legacy?null:keys.size());out.put("branchReady",branchReady);
   out.put("workingCount",working);out.put("legacy",legacy);out.put("places",places);out.put("pickupDate",date);
   out.put("pickupOverdue",date!=null&&date.isBefore(LocalDate.now(java.time.ZoneId.of("Europe/Kyiv"))));
   out.put("needsNotice",PickupRules.needsNotice(keys,notified));
   out.put("pickupStatus",PickupRules.status(keys,notified,last,Objects.toString(first.get("lastOutcome"),""),date,LocalDate.now(java.time.ZoneId.of("Europe/Kyiv"))));
   result.add(out);
  }
  return result;
 }
 private Set<String> snapshot(Object raw) {
  if(raw==null)return Set.of();
  try {var result=new HashSet<String>();for(var n:json.readTree(raw.toString()))result.add(n.asText());return result;}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 private String hashPickup(List<String> state) {
  try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("|",state).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private PreviewException fail(HttpStatus status,String message){return new PreviewException(status,"CRM_ERROR",message);}
}
