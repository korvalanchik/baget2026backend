package com.example.baget.commercial;
import com.example.baget.calculation.preview.*;
import com.example.baget.users.*;
import com.example.baget.orderproduct.ProductionRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CommercialService {
 private final JdbcTemplate db;private final UsersRepository users;private final PartPriceResolver prices;private final ObjectMapper json;
 public CommercialService(JdbcTemplate db,UsersRepository users,PartPriceResolver prices,ObjectMapper json){this.db=db;this.users=users;this.prices=prices;this.json=json;}
 private User user(Authentication a){if(a==null || !a.isAuthenticated())throw denied();var u=users.findByUsername(a.getName()).orElseThrow(CommercialService::denied);if(!role(u,"ROLE_ADMIN","ROLE_COUNTER","ROLE_LEVEL2","ROLE_MANAGER","ROLE_SELLER","ROLE_FOREMAN","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER"))throw denied();return u;}
 private boolean role(User u,String... names){return u.getRoles().stream().anyMatch(r->Set.of(names).contains(r.getName()));}
 private void branch(User u,long branch){if(!role(u,"ROLE_ADMIN") && u.getAllowedBranches().stream().noneMatch(b->b.getBranchNo()==branch))throw denied();}
 public List<Map<String,Object>> branches(Authentication a){var u=user(a);var result=db.queryForList("SELECT b.BranchNo AS branchNo,b.BranchName AS branchName,s.mode,s.partner_id AS partnerId,c.Company AS partnerName,COALESCE(s.revision,0) AS revision FROM branches b LEFT JOIN branch_commercial_settings s ON s.branch_no=b.BranchNo LEFT JOIN customer c ON c.CustNo=s.partner_id ORDER BY b.BranchName");return result.stream().filter(x->role(u,"ROLE_ADMIN") || u.getAllowedBranches().stream().anyMatch(v->v.getBranchNo().equals(((Number)x.get("branchNo")).longValue()))).toList();}
 public record Settings(@PositiveOrZero long expectedRevision,@NotBlank String mode,Long partnerId,@NotBlank @Size(max=1000) String note){}
 public Object configure(long branch,Settings r,Authentication a){
  var u=user(a);if(!role(u,"ROLE_ADMIN"))throw denied();
  if(!Set.of("OWN","RESELLER").contains(r.mode()) || ("RESELLER".equals(r.mode()) && r.partnerId()==null) || ("OWN".equals(r.mode()) && r.partnerId()!=null))throw conflict("Вкажіть тип філії та фірму для реселлера");
  db.queryForObject("SELECT BranchNo FROM branches WHERE BranchNo=? FOR UPDATE",Long.class,branch);
  var rows=db.queryForList("SELECT revision FROM branch_commercial_settings WHERE branch_no=?",branch);
  long rev=rows.isEmpty()?0:((Number)rows.get(0).get("revision")).longValue();if(rev!=r.expectedRevision())throw conflict("Налаштування змінилися. Оновіть список");
  if(r.partnerId()!=null && db.queryForObject("SELECT COUNT(*) FROM customer WHERE CustNo=?",Long.class,r.partnerId())==0)throw conflict("Фірму не знайдено у клієнтах");
  db.update("INSERT INTO branch_commercial_settings(branch_no,mode,partner_id,revision) VALUES(?,?,?,1) ON DUPLICATE KEY UPDATE mode=VALUES(mode),partner_id=VALUES(partner_id),revision=revision+1",branch,r.mode(),r.partnerId());
  db.update("INSERT INTO branch_commercial_events(branch_no,revision,mode,partner_id,actor,note) VALUES(?,?,?,?,?,?)",branch,rev+1,r.mode(),r.partnerId(),a.getName(),r.note());return branches(a);
 }
 public Map<String,Object> settings(String name,Authentication a){
  var rows=db.queryForList("SELECT b.BranchNo AS branchNo,s.mode,s.partner_id AS partnerId FROM branches b LEFT JOIN branch_commercial_settings s ON s.branch_no=b.BranchNo WHERE b.BranchName=? FOR SHARE",name);
  if(rows.isEmpty())throw conflict("Філію не знайдено");var row=rows.get(0);branch(user(a),((Number)row.get("branchNo")).longValue());
  if(row.get("mode")==null)throw conflict("Адміністратор має налаштувати тип філії та прайс перед новим замовленням");return row;
 }
 public Authentication pricing(Authentication a,String mode){return mode==null?a:prices.forTier(a,mode);}
 public Authentication pricingBranch(Authentication a,String branch){return pricing(a,settings(branch,a).get("mode").toString());}
 public Authentication pricingOrder(long order,Authentication a){var row=db.queryForMap("SELECT commerce_mode,BranchNo FROM orders WHERE OrderNo=?",order);branch(user(a),((Number)row.get("BranchNo")).longValue());return pricing(a,(String)row.get("commerce_mode"));}
 public void initialize(long order,long product,String actor){
  var mode=db.queryForObject("SELECT commerce_mode FROM orders WHERE OrderNo=?",String.class,order);if(mode==null)return;
  for(var row:db.queryForList("SELECT i.ItemNo AS itemNo,i.billing_unit_price AS retail,p.ListPrice_1 AS reseller FROM items i JOIN parts p ON p.PartNo=i.PartNo WHERE i.OrderNo=? AND i.order_product_id=?",order,product)){
   Object raw="RESELLER".equals(mode)?row.get("reseller"):row.get("retail");if(raw==null)throw conflict("Для матеріалу відсутній реселлерський прайс");BigDecimal price=new BigDecimal(raw.toString());if(price.signum()<0)throw conflict("Некоректний реселлерський прайс");
   db.update("UPDATE items SET reseller_unit_price=? WHERE OrderNo=? AND ItemNo=?",price,order,row.get("itemNo"));
  }
  db.update("INSERT INTO product_price_approval(product_id,baseline_retail,baseline_reseller) SELECT p.id,p.total,(SELECT COALESCE(SUM(ROUND(i.planned_consumption*i.reseller_unit_price,2)),0) FROM items i WHERE i.order_product_id=p.id) FROM order_products p WHERE p.id=?",product);
 }
 private Map<String,Object> snapshot(long pid){
  var rows=db.queryForList("SELECT ItemNo,PartNo,actual_consumption,actual_width_mm,actual_height_mm,billing_unit_price,reseller_unit_price FROM items WHERE order_product_id=? ORDER BY ItemNo",pid);
  var shape=db.queryForMap("SELECT measured_width_mm,measured_height_mm FROM order_products WHERE id=?",pid);
  if(shape.get("measured_width_mm")==null || shape.get("measured_height_mm")==null || rows.isEmpty())throw conflict("Спочатку збережіть обмір і витрати");
  BigDecimal retail=BigDecimal.ZERO,reseller=BigDecimal.ZERO;
  for(var row:rows){if(row.get("actual_consumption")==null || row.get("billing_unit_price")==null || row.get("reseller_unit_price")==null)throw conflict("Не заповнено фактичні витрати або ціни");var qty=new BigDecimal(row.get("actual_consumption").toString());retail=retail.add(ProductionRules.amount(qty,new BigDecimal(row.get("billing_unit_price").toString())));reseller=reseller.add(ProductionRules.amount(qty,new BigDecimal(row.get("reseller_unit_price").toString())));}
  var result=new LinkedHashMap<String,Object>();result.put("shape",shape);result.put("lines",rows);result.put("retail",retail);result.put("reseller",reseller);return result;
 }
 public void measured(long pid,String actor,String note){
  var rows=db.queryForList("SELECT * FROM product_price_approval WHERE product_id=?",pid);if(rows.isEmpty())return;var old=rows.get(0);var snapshot=snapshot(pid);String hash=hash(snapshot);
  if(hash.equals(old.get("calculation_hash")))return;
  var baseline=(BigDecimal)old.get("baseline_retail");boolean waiting=PriceTolerance.requiresApproval(baseline,(BigDecimal)snapshot.get("retail"));
  db.update("UPDATE product_price_approval SET current_retail=?,reseller_total=?,state=?,revision=revision+1,calculation_hash=? WHERE product_id=?",snapshot.get("retail"),snapshot.get("reseller"),waiting?"WAITING":"AUTO",hash,pid);
  if(waiting)db.update("UPDATE order_products SET production_state='ACCEPTED' WHERE id=? AND production_state='IN_PROGRESS'",pid);
  event(pid,((Number)old.get("revision")).longValue()+1,waiting?"WAITING":"AUTO",actor,note,snapshot);
 }
 public void requireReady(long pid){
  var rows=db.queryForList("SELECT state,calculation_hash FROM product_price_approval WHERE product_id=?",pid);if(rows.isEmpty())return;var row=rows.get(0);
  if(!Set.of("AUTO","APPROVED").contains(row.get("state")))throw conflict("Виготовлення заблоковане: збережіть обмір та отримайте погодження ціни, якщо відхилення більше 5%");
  if(!hash(snapshot(pid)).equals(row.get("calculation_hash")))throw conflict("Розрахунок змінився. Збережіть обмір для повторної перевірки ціни");
 }
 private Map<String,Object> product(long pid,Authentication a,boolean lock){
  var rows=db.queryForList("SELECT p.order_no AS orderNo,o.BranchNo AS branchNo,o.commerce_mode AS mode,o.commerce_partner_id AS partnerId FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=? AND p.active=1",pid);if(rows.isEmpty())throw conflict("Виріб не знайдено");var row=rows.get(0);branch(user(a),((Number)row.get("branchNo")).longValue());
  if(lock)db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,row.get("orderNo"));return row;
 }
 public Object read(long pid,Authentication a){var context=product(pid,a,false);var rows=db.queryForList("SELECT baseline_retail AS baseline,baseline_reseller AS baselineReseller,current_retail AS retail,reseller_total AS reseller,state,revision FROM product_price_approval WHERE product_id=?",pid);var result=new HashMap<String,Object>(context);result.put("pricing",rows.isEmpty()?null:rows.get(0));result.put("canApprove",role(user(a),"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER"));result.put("events",db.queryForList("SELECT revision,action,actor,note,snapshot,created_at AS createdAt FROM product_price_events WHERE product_id=? ORDER BY id DESC LIMIT 100",pid));return result;}
 public record Decision(@Positive long expectedRevision,boolean approve,@NotBlank @Size(max=2000) String note){}
 public Object decide(long pid,Decision r,Authentication a){
  var u=user(a);if(!role(u,"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER"))throw denied();var context=product(pid,a,true);
  if(db.queryForObject("SELECT COUNT(*) FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=? AND p.active=1 AND p.production_confirmed=0 AND o.StatusOrder NOT IN (5,10)",Long.class,pid)==0)throw conflict("Виріб уже завершено або скасовано");
  var rows=db.queryForList("SELECT state,revision,calculation_hash FROM product_price_approval WHERE product_id=?",pid);if(rows.isEmpty())throw conflict("Немає розрахунку для погодження");var row=rows.get(0);
  if(((Number)row.get("revision")).longValue()!=r.expectedRevision() || !Set.of("WAITING","REJECTED").contains(row.get("state")))throw conflict("Ціну вже змінено або погоджено. Оновіть картку");
  var snapshot=snapshot(pid);if(!hash(snapshot).equals(row.get("calculation_hash")))throw conflict("Обмір змінився. Потрібен новий розрахунок");
  db.update("UPDATE product_price_approval SET state=?,revision=revision+1 WHERE product_id=?",r.approve()?"APPROVED":"REJECTED",pid);
  event(pid,r.expectedRevision()+1,r.approve()?"APPROVED":"REJECTED",a.getName(),r.note(),snapshot);
  db.update("UPDATE orders SET calculation_revision=calculation_revision+1 WHERE OrderNo=?",context.get("orderNo"));return read(pid,a);
 }
 public Object pending(Authentication a){var u=user(a);return db.queryForList("SELECT p.id AS productId,p.order_no AS orderNo,p.client_product_id AS productName,o.BranchNo AS branchNo,b.BranchName AS branchName,c.Company AS customerName,x.baseline_retail AS baseline,x.current_retail AS retail FROM product_price_approval x JOIN order_products p ON p.id=x.product_id JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo JOIN customer c ON c.CustNo=o.CustNo WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND x.state IN ('WAITING','REJECTED') ORDER BY p.id").stream().filter(row->role(u,"ROLE_ADMIN") || u.getAllowedBranches().stream().anyMatch(v->v.getBranchNo().equals(((Number)row.get("branchNo")).longValue()))).toList();}
 private void event(long pid,long revision,String action,String actor,String note,Object snapshot){db.update("INSERT INTO product_price_events(product_id,revision,action,actor,note,snapshot) VALUES(?,?,?,?,?,?)",pid,revision,action,actor,note,encode(snapshot));}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"COMMERCIAL_ACCESS","Немає доступу до цієї дії або філії");}
 private static PreviewException conflict(String message){return new PreviewException(HttpStatus.CONFLICT,"PRICE_APPROVAL_REQUIRED",message);}
}
