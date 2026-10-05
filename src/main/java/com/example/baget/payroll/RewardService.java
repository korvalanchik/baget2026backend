package com.example.baget.payroll;
import com.example.baget.users.*;
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
import java.sql.Timestamp;
import java.util.*;
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class RewardService {
 private final JdbcTemplate db;private final UsersRepository users;private final ObjectMapper json;
 public RewardService(JdbcTemplate db,UsersRepository users,ObjectMapper json){this.db=db;this.users=users;this.json=json;}
 private User user(Authentication a){if(a==null||!a.isAuthenticated())throw denied();var u=users.findByUsername(a.getName()).orElseThrow(RewardService::denied);if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2").contains(r.getName())))throw denied();return u;}
 private boolean role(User u,String r){return u.getRoles().stream().anyMatch(x->r.equals(x.getName()));}
 private boolean editor(User u){return role(u,"ROLE_ADMIN")||role(u,"ROLE_COUNTER")||role(u,"ROLE_ACCOUNTANT")||role(u,"ROLE_BOOKKEEPER");}
 private void branch(User u,long b){if(!role(u,"ROLE_ADMIN")&&u.getAllowedBranches().stream().noneMatch(x->x.getBranchNo()==b))throw denied();}
 private String scope(User u,List<Object> args,String column){if(role(u,"ROLE_ADMIN"))return "";var ids=u.getAllowedBranches().stream().map(x->x.getBranchNo()).toList();if(ids.isEmpty())return " AND 1=0 ";args.addAll(ids);return " AND "+column+" IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ";}
 private BigDecimal balance(long account){return db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM reward_entries WHERE account_id=?",BigDecimal.class,account);}
 private long wallet(long manager,long branch){db.update("INSERT INTO reward_accounts(user_id,branch_no) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",manager,branch);return db.queryForObject("SELECT id FROM reward_accounts WHERE user_id=? AND branch_no=? FOR UPDATE",Long.class,manager,branch);}
 private Map<String,Object> account(long id,User u,boolean lock){var rows=db.queryForList("SELECT id,user_id AS userId,branch_no AS branchNo,revision FROM reward_accounts WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw conflict("Рахунок менеджера не знайдено");var r=rows.get(0);if(editor(u))branch(u,num(r,"branchNo"));else if(num(r,"userId")!=u.getId())throw denied();return r;}
 /** Called within the same order handover transaction, after physical acceptance by CUSTOMER. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void accrue(long product,String actor){
  var settings=db.queryForMap("SELECT enabled,rate,revision FROM reward_settings WHERE id=1 FOR SHARE");if(!enabled(settings.get("enabled")))return;
  var p=db.queryForMap("SELECT p.order_no AS orderNo,o.BranchNo AS branchNo,o.EmpNo AS authorId,o.commerce_mode AS mode,p.current_location AS location,p.production_confirmed AS confirmed FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=?",product);
  if(!"OWN".equals(p.get("mode")))return;
  if(!"CUSTOMER".equals(p.get("location")))throw conflict("Нарахування можливе лише після фактичної видачі");
  if(db.queryForObject("SELECT COUNT(*) FROM reward_issues WHERE product_id=?",Long.class,product)>0)return;
  Long author=p.get("authorId") instanceof Number n?n.longValue():null;
  boolean valid=author!=null&&db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,author)>0;
  var rates=valid?db.queryForList("SELECT rate FROM reward_rates WHERE user_id=?",author):List.<Map<String,Object>>of();
  BigDecimal rate=decimal(rates.isEmpty()?settings:rates.get(0),"rate");
  // Invoice claim follows invoice merge ownership and preserves the final billed retail amount.
  var billed=db.queryForList("SELECT ip.billed_amount AS amount FROM product_invoice_claims c JOIN invoice_products ip ON ip.invoice_id=c.invoice_id AND ip.product_id=c.product_id JOIN invoices i ON i.id=c.invoice_id WHERE c.product_id=? AND i.lifecycle='ACTIVE' AND i.status NOT IN ('DRAFT','CANCELLED')",product);
  BigDecimal base=billed.size()==1?decimal(billed.get(0),"amount"):null;
  String reason=!valid?"Не визначено автора замовлення":!enabled(p.get("confirmed"))?"Не підтверджено фактичний розрахунок":base==null||base.signum()<0?"Немає однозначної остаточної суми виробу в рахунку":null;
  db.update("INSERT INTO reward_issues(product_id,order_no,branch_no,author_id,base_amount,rate,personal_rate,state,reason,actor) VALUES(?,?,?,?,?,?,?,?,?,?)",product,p.get("orderNo"),p.get("branchNo"),author,base,rate,!rates.isEmpty(),reason==null?"POSTED":"REVIEW",reason,actor);
  if(reason!=null)return;
  long id=wallet(author,num(p,"branchNo"));
  db.update("INSERT INTO reward_entries(account_id,kind,product_id,amount,note,actor) VALUES(?,'ACCRUAL',?,?,?,?)",id,product,RewardMath.calculate(base,rate),"Видача виробу клієнту",actor);
  db.update("UPDATE reward_accounts SET revision=revision+1 WHERE id=?",id);
 }
 public Object settings(Authentication a){var u=user(a);if(!role(u,"ROLE_ADMIN"))throw denied();return Map.of("settings",db.queryForMap("SELECT enabled,rate,revision FROM reward_settings WHERE id=1"),"users",db.queryForList("SELECT u.id,u.username,r.rate FROM users u LEFT JOIN reward_rates r ON r.user_id=u.id ORDER BY u.username"));}
 public record Settings(@NotBlank String requestKey,@PositiveOrZero long revision,boolean enabled,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal rate,Long userId,@DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal personalRate){}
 public Object saveSettings(Settings r,Authentication a){var u=user(a);if(!role(u,"ROLE_ADMIN"))throw denied();var prior=retry("SETTINGS",r.requestKey(),r,a);if(prior!=null)return prior;var old=db.queryForMap("SELECT revision FROM reward_settings WHERE id=1 FOR UPDATE");prior=retry("SETTINGS",r.requestKey(),r,a);if(prior!=null)return prior;if(num(old,"revision")!=r.revision())throw conflict("Налаштування змінилися. Оновіть форму");
  if(r.userId()!=null){if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,r.userId())!=1)throw conflict("Менеджера не знайдено");if(r.personalRate()==null)db.update("DELETE FROM reward_rates WHERE user_id=?",r.userId());else db.update("INSERT INTO reward_rates(user_id,rate) VALUES(?,?) ON DUPLICATE KEY UPDATE rate=VALUES(rate)",r.userId(),r.personalRate());}
  db.update("UPDATE reward_settings SET enabled=?,rate=?,revision=revision+1 WHERE id=1",r.enabled(),r.rate());return remember("SETTINGS",r.requestKey(),r,Map.of("saved",true),a);
 }
 private Timestamp[] period(LocalDate from,LocalDate to){if(from==null||to==null||to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>3660)throw conflict("Вкажіть період до 10 років");var zone=ZoneId.of("Europe/Kyiv");return new Timestamp[]{Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant())};}
 public Object accounts(LocalDate from,LocalDate to,Authentication a){var u=user(a);var dates=period(from,to);var args=new ArrayList<Object>(List.of(dates[0],dates[1],dates[0],dates[1]));String filter;if(editor(u))filter=scope(u,args,"ra.branch_no");else{filter=" AND ra.user_id=? ";args.add(u.getId());}
  var rows=db.queryForList("SELECT ra.id,u.username,b.BranchName AS branchName,ra.revision,COALESCE(SUM(CASE WHEN e.kind IN ('ACCRUAL','CORRECTION') AND e.created_at>=? AND e.created_at<? THEN e.amount ELSE 0 END),0) AS earned,COALESCE(-SUM(CASE WHEN e.kind IN ('PAYOUT','REVERSAL') AND e.created_at>=? AND e.created_at<? THEN e.amount ELSE 0 END),0) AS paid,COALESCE(SUM(e.amount),0) AS balance FROM reward_accounts ra JOIN users u ON u.id=ra.user_id JOIN branches b ON b.BranchNo=ra.branch_no LEFT JOIN reward_entries e ON e.account_id=ra.id WHERE 1=1 "+filter+" GROUP BY ra.id,u.username,b.BranchName,ra.revision ORDER BY u.username,b.BranchName",args.toArray());
  return Map.of("rows",rows,"canEdit",editor(u),"canConfigure",role(u,"ROLE_ADMIN"));
 }
 public Object history(long id,int page,Authentication a){return history(id,page,LocalDate.now(ZoneId.of("Europe/Kyiv")).withDayOfMonth(1),LocalDate.now(ZoneId.of("Europe/Kyiv")),a);}
 public Object history(long id,int page,LocalDate from,LocalDate to,Authentication a){var dates=period(from,to);if(page<0||page>100000)throw conflict("Некоректна сторінка");var u=user(a);var row=account(id,u,false);row.put("balance",balance(id));row.put("rows",db.queryForList("SELECT e.id,e.kind,e.product_id AS productId,e.source_id AS sourceId,e.amount,e.method,e.note,e.actor,e.created_at AS createdAt,ri.order_no AS orderNo,ri.base_amount AS baseAmount,ri.rate FROM reward_entries e LEFT JOIN reward_issues ri ON ri.product_id=e.product_id WHERE e.account_id=? AND e.created_at>=? AND e.created_at<? ORDER BY e.id DESC LIMIT 51 OFFSET ?",id,dates[0],dates[1],page*50));return row;}
 public record Operation(@NotBlank String requestKey,@Positive long accountId,@PositiveOrZero long revision,@NotBlank String kind,@NotNull @Digits(integer=15,fraction=2) BigDecimal amount,@Size(max=16) String method,@Positive Long sourceId,@NotBlank @Size(max=1000) String note){}
 public Object post(Operation r,Authentication a){var u=user(a);if(!editor(u))throw denied();account(r.accountId(),u,false);var prior=retry("ENTRY",r.requestKey(),r,a);if(prior!=null)return prior;var acc=account(r.accountId(),u,true);prior=retry("ENTRY",r.requestKey(),r,a);if(prior!=null)return prior;if(num(acc,"revision")!=r.revision())throw conflict("Нарахування або виплати змінилися. Оновіть дані");
  Long product=null;
  if("PAYOUT".equals(r.kind())){if(r.amount().signum()<=0||r.amount().compareTo(balance(r.accountId()))>0)throw conflict("Виплата має бути додатною та не перевищувати залишок");if(!Set.of("CASH","BANK").contains(Objects.toString(r.method(),""))||r.sourceId()!=null)throw conflict("Вкажіть спосіб виплати");}
  else if("CORRECTION".equals(r.kind())){if(r.sourceId()==null||r.amount().signum()==0)throw conflict("Вкажіть початкове нарахування і ненульове коригування");var rows=db.queryForList("SELECT e.product_id AS productId,ri.base_amount AS baseAmount FROM reward_entries e JOIN reward_issues ri ON ri.product_id=e.product_id WHERE e.id=? AND e.account_id=? AND e.kind='ACCRUAL'",r.sourceId(),r.accountId());if(rows.isEmpty())throw conflict("Нарахування не належить цьому рахунку");product=num(rows.get(0),"productId");var total=db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM reward_entries WHERE id=? OR (source_id=? AND kind='CORRECTION')",BigDecimal.class,r.sourceId(),r.sourceId()).add(r.amount());if(total.signum()<0||total.compareTo(decimal(rows.get(0),"baseAmount"))>0)throw conflict("Винагорода після коригування має бути від 0 до вартості виробу");}
  else if("REVERSAL".equals(r.kind())){if(r.sourceId()==null)throw conflict("Оберіть помилкову виплату");var rows=db.queryForList("SELECT amount FROM reward_entries WHERE id=? AND account_id=? AND kind='PAYOUT'",r.sourceId(),r.accountId());if(rows.isEmpty()||r.amount().compareTo(decimal(rows.get(0),"amount").negate())!=0)throw conflict("Потрібно скасувати точну суму виплати");if(db.queryForObject("SELECT COUNT(*) FROM reward_entries WHERE source_id=? AND kind='REVERSAL'",Long.class,r.sourceId())>0)throw conflict("Виплату вже скасовано");}
  else throw conflict("Невідома операція");
  db.update("INSERT INTO reward_entries(account_id,kind,product_id,source_id,amount,method,note,actor) VALUES(?,?,?,?,?,?,?,?)",r.accountId(),r.kind(),product,r.sourceId(),"PAYOUT".equals(r.kind())?r.amount().negate():r.amount(),"PAYOUT".equals(r.kind())?r.method():null,r.note().trim(),a.getName());db.update("UPDATE reward_accounts SET revision=revision+1 WHERE id=?",r.accountId());return remember("ENTRY",r.requestKey(),r,Map.of("saved",true),a);
 }
 public Object reviewUsers(Authentication a){var u=user(a);if(!editor(u))throw denied();if(role(u,"ROLE_ADMIN"))return db.queryForList("SELECT id,username FROM users ORDER BY username");var args=new ArrayList<Object>();return db.queryForList("SELECT DISTINCT u.id,u.username FROM users u JOIN user_branch_access ub ON ub.user_id=u.id WHERE 1=1 "+scope(u,args,"ub.branch_no")+" ORDER BY u.username",args.toArray());}
 public Object review(Authentication a){var u=user(a);if(!editor(u))throw denied();var args=new ArrayList<Object>();return db.queryForList("SELECT product_id AS productId,order_no AS orderNo,branch_no AS branchNo,author_id AS authorId,base_amount AS baseAmount,rate,reason,issued_at AS issuedAt FROM reward_issues WHERE state='REVIEW' "+scope(u,args,"branch_no")+" ORDER BY issued_at,product_id LIMIT 200",args.toArray());}

 public record Resolve(@NotBlank String requestKey,@Positive long productId,@Positive long userId,@NotNull @DecimalMin("0") @Digits(integer=15,fraction=2) BigDecimal base,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal rate,@NotBlank @Size(max=1000) String note){}
 public Object resolve(Resolve r,Authentication a){var u=user(a);if(!editor(u))throw denied();var rows=db.queryForList("SELECT * FROM reward_issues WHERE product_id=? FOR UPDATE",r.productId());if(rows.isEmpty())throw conflict("Видачу не знайдено");var old=rows.get(0);branch(u,num(old,"branch_no"));var prior=retry("RESOLVE",r.requestKey(),r,a);if(prior!=null)return prior;if(!"REVIEW".equals(old.get("state")))throw conflict("Нарахування вже уточнено");if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,r.userId())!=1)throw conflict("Користувача не знайдено");long id=wallet(r.userId(),num(old,"branch_no"));db.update("UPDATE reward_issues SET author_id=?,base_amount=?,rate=?,personal_rate=FALSE,state='POSTED' WHERE product_id=?",r.userId(),r.base(),r.rate(),r.productId());db.update("INSERT INTO reward_entries(account_id,kind,product_id,amount,note,actor) VALUES(?,'ACCRUAL',?,?,?,?)",id,r.productId(),RewardMath.calculate(r.base(),r.rate()),r.note().trim(),a.getName());db.update("UPDATE reward_accounts SET revision=revision+1 WHERE id=?",id);return remember("RESOLVE",r.requestKey(),r,Map.of("saved",true,"before",old),a);}
 private Object retry(String op,String key,Object payload,Authentication a){try{if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException();}catch(Exception e){throw conflict("Некоректний ключ операції");}var rows=db.queryForList("SELECT actor,operation,payload,result FROM reward_requests WHERE request_key=?",key);if(rows.isEmpty())return null;var old=rows.get(0);try{if(!a.getName().equals(old.get("actor"))||!op.equals(old.get("operation"))||!json.readTree(encode(payload)).equals(json.readTree(old.get("payload").toString())))throw conflict("Ключ використано для іншої операції");return json.readValue(old.get("result").toString(),Map.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private Object remember(String op,String key,Object payload,Object result,Authentication a){db.update("INSERT INTO reward_requests(request_key,actor,operation,payload,result) VALUES(?,?,?,?,?)",key,a.getName(),op,encode(payload),encode(result));return result;}
 private String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
 private static long num(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 private static BigDecimal decimal(Map<String,Object> r,String k){return r.get(k)==null?null:new BigDecimal(r.get(k).toString());}
 private static boolean enabled(Object x){return Boolean.TRUE.equals(x)||(x instanceof Number n&&n.intValue()==1);}
 private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"REWARD_ACCESS","Немає доступу до винагород цього менеджера або філії");}
 private static PreviewException conflict(String x){return new PreviewException(HttpStatus.CONFLICT,"REWARD_CONFLICT",x);}
}
