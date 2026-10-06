package com.example.baget.costupdate;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CostUpdateService {
 private final JdbcTemplate db;private final UsersRepository users;private final ObjectMapper json;
 public CostUpdateService(JdbcTemplate db,UsersRepository users,ObjectMapper json){this.db=db;this.users=users;this.json=json;}
 public record Rate(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long revision,@Positive Long partNo,@DecimalMin("0") @DecimalMax("1000") @Digits(integer=4,fraction=2) BigDecimal rate){}
 public record Selection(@Positive long partNo,@NotBlank String token){}
 public record Apply(@NotBlank @Size(max=36) String requestKey,@NotEmpty @Size(max=100) List<@NotNull @Valid Selection> rows){}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"COST_ACCESS","Оновлення собівартості доступне адміністратору");}
 private PreviewException bad(String text){return new PreviewException(HttpStatus.CONFLICT,"COST_CHANGED",text);}
 private String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
 private Object tree(Object x){try{return json.readTree(x.toString());}catch(Exception e){throw new IllegalStateException(e);}}
 private BigDecimal decimal(Object x){return x==null?null:new BigDecimal(x.toString());}
 private String token(Object x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(x).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> settings(boolean lock){return db.queryForMap("SELECT rate,revision FROM cost_update_settings WHERE id=1"+(lock?" FOR UPDATE":""));}
 private static final String PART="SELECT p.PartNo AS partNo,p.Description AS description,p.unit_type AS unit,CAST(p.Cost AS DECIMAL(24,6)) AS oldCost,p.Version AS version,CASE WHEN p.inventory_cost_unit=p.unit_type THEN p.inventory_unit_cost ELSE NULL END AS baseCost,r.rate AS personalRate FROM parts p LEFT JOIN cost_update_rates r ON r.part_no=p.PartNo WHERE p.part_kind='MATERIAL'";
 private Map<String,Object> decorate(Map<String,Object> p,Map<String,Object> s){
  var rate=decimal(p.get("personalRate")!=null?p.get("personalRate"):s.get("rate"));p.put("rate",rate);p.put("settingsRevision",s.get("revision"));
  String reason=null;BigDecimal next=null;try{next=CostUpdateRules.calculate(decimal(p.get("baseCost")),rate);}catch(IllegalArgumentException e){reason=p.get("baseCost")==null?"Складська собівартість невідома":e.getMessage();}
  p.put("newCost",next);var old=decimal(p.get("oldCost"));p.put("differencePercent",next!=null&&old!=null&&old.signum()>0?next.subtract(old).multiply(new BigDecimal("100")).divide(old,2,RoundingMode.HALF_UP):null);
  p.put("reason",reason);p.put("token",token(p));return p;
 }
 public Object list(String q,int page,Authentication a){access(a);var s=settings(false);String search=q==null?"":q.trim();if(search.length()>100)search=search.substring(0,100);
  var rows=db.queryForList(PART+" AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0) ORDER BY p.PartNo LIMIT 100 OFFSET ?",search,search,Math.max(0,Math.min(page,100000))*100);
  rows.forEach(p->decorate(p,s));return Map.of("settings",s,"rows",rows);
 }
 private Object duplicate(String key,Object body,Authentication a){try{UUID.fromString(key);}catch(IllegalArgumentException e){throw bad("Некоректний ключ запиту");}
  var found=db.queryForList("SELECT actor,request_json,snapshot FROM cost_update_events WHERE request_key=?",key);
  if(found.isEmpty())return null;var f=found.get(0);if(!a.getName().equals(f.get("actor"))||!tree(f.get("request_json")).equals(tree(encode(body))))throw bad("Ключ вже використано для іншої операції");return tree(f.get("snapshot"));
 }
 private void event(String key,String action,Object body,Object snapshot,Authentication a){db.update("INSERT INTO cost_update_events(request_key,action,actor,request_json,snapshot) VALUES(?,?,?,?,?)",key,action,a.getName(),encode(body),encode(snapshot));}
 public Object rate(Rate r,Authentication a){access(a);var s=settings(true);Object prior=duplicate(r.requestKey(),r,a);if(prior!=null)return prior;
  if(((Number)s.get("revision")).longValue()!=r.revision())throw bad("Налаштування змінилися. Оновіть список");
  Object before=s.get("rate");
  if(r.partNo()==null){if(r.rate()==null)throw bad("Вкажіть загальний відсоток");db.update("UPDATE cost_update_settings SET rate=? WHERE id=1",r.rate());}
  else{
   if(db.queryForList("SELECT PartNo FROM parts WHERE PartNo=? AND part_kind='MATERIAL' FOR UPDATE",r.partNo()).isEmpty())throw bad("Матеріал не знайдено");
   var old=db.queryForList("SELECT rate FROM cost_update_rates WHERE part_no=?",r.partNo());before=old.isEmpty()?null:old.get(0).get("rate");
   if(r.rate()==null)db.update("DELETE FROM cost_update_rates WHERE part_no=?",r.partNo());
   else db.update("INSERT INTO cost_update_rates(part_no,rate) VALUES(?,?) ON DUPLICATE KEY UPDATE rate=VALUES(rate)",r.partNo(),r.rate());
  }
  db.update("UPDATE cost_update_settings SET revision=revision+1 WHERE id=1");var result=new LinkedHashMap<String,Object>();result.put("partNo",r.partNo());result.put("before",before);result.put("after",r.rate());event(r.requestKey(),"RATE",r,result,a);return result;
 }
 public Object apply(Apply r,Authentication a){access(a);var s=settings(true);Object prior=duplicate(r.requestKey(),r,a);if(prior!=null)return prior;
  var sorted=r.rows().stream().sorted(Comparator.comparingLong(Selection::partNo)).toList();var ids=new HashSet<Long>();var result=new ArrayList<Map<String,Object>>();
  for(var row:sorted){if(!ids.add(row.partNo()))throw bad("Матеріал повторюється");var found=db.queryForList(PART+" AND p.PartNo=? FOR UPDATE",row.partNo());if(found.isEmpty())throw bad("Матеріал не знайдено");var p=decorate(found.get(0),s);
   if(!row.token().equals(p.get("token"))||p.get("newCost")==null)throw bad("Дані матеріалу "+row.partNo()+" змінилися. Оновіть перегляд");result.add(p);
  }
  for(var p:result){db.update("UPDATE parts SET Cost=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",p.get("newCost"),p.get("partNo"));
   var stored=db.queryForObject("SELECT CAST(Cost AS DECIMAL(24,2)) FROM parts WHERE PartNo=?",BigDecimal.class,p.get("partNo"));
   if(stored==null||stored.compareTo((BigDecimal)p.get("newCost"))!=0)throw bad("Поле Parts.Cost не зберігає ціну з точністю до копійки. Потрібно перевірити його тип у базі");
  }
  var snapshot=Map.of("rows",result);event(r.requestKey(),"APPLY",r,snapshot,a);return snapshot;
 }
 public Object history(int page,Authentication a){access(a);var rows=db.queryForList("SELECT id,action,actor,created_at AS createdAt,snapshot FROM cost_update_events ORDER BY id DESC LIMIT 30 OFFSET ?",Math.max(0,Math.min(page,100000))*30);rows.forEach(r->r.put("snapshot",tree(r.get("snapshot"))));return rows;}
}
