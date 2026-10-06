package com.example.baget.openingcost;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OpeningCostService {
 private final JdbcTemplate db;private final UsersRepository users;private final ObjectMapper json;
 public OpeningCostService(JdbcTemplate db,UsersRepository users,ObjectMapper json){this.db=db;this.users=users;this.json=json;}
 public record Request(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long version,@NotNull @Positive BigDecimal stock,@NotBlank String unit,
  @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal unitCost,@NotBlank @Size(max=1000) String note){}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"OPENING_COST_ACCESS","Встановлення початкової собівартості доступне адміністратору");}
 private PreviewException bad(String message){return new PreviewException(HttpStatus.CONFLICT,"OPENING_COST_CHANGED",message);}
 private String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
 private Object tree(Object x){try{return json.readTree(x.toString());}catch(Exception e){throw new IllegalStateException(e);}}
 private Map<String,Object> part(long id,boolean lock){var rows=db.queryForList("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,CAST(OnHand AS DECIMAL(24,6)) AS stock,COALESCE(Version,0) AS version,CASE WHEN inventory_cost_unit=unit_type THEN inventory_unit_cost ELSE NULL END AS unitCost FROM parts WHERE PartNo=? AND part_kind='MATERIAL'"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw bad("Матеріал не знайдено");return rows.get(0);}
 public Object read(long id,Authentication a){access(a);return part(id,false);}
 public Object history(long id,int page,Authentication a){access(a);var rows=db.queryForList("SELECT id,actor,created_at AS createdAt,snapshot FROM opening_material_cost_events WHERE part_no=? ORDER BY id DESC LIMIT 20 OFFSET ?",id,Math.max(0,Math.min(page,100000))*20);rows.forEach(r->r.put("snapshot",tree(r.get("snapshot"))));return rows;}
 public Object save(long id,Request r,Authentication a){access(a);try{UUID.fromString(r.requestKey());}catch(IllegalArgumentException e){throw bad("Некоректний ключ запиту");}
  var p=part(id,true);
  var previous=db.queryForList("SELECT part_no,actor,request_json,snapshot FROM opening_material_cost_events WHERE request_key=?",r.requestKey());
  if(!previous.isEmpty()){var old=previous.get(0);if(((Number)old.get("part_no")).longValue()!=id||!a.getName().equals(old.get("actor"))||!tree(old.get("request_json")).equals(tree(encode(r))))throw bad("Ключ використано для іншої операції");return tree(old.get("snapshot"));}
  if(p.get("unitCost")!=null)throw bad("Собівартість вже задана. Оновіть картку матеріалу");
  var stock=p.get("stock")==null?null:new BigDecimal(p.get("stock").toString());
  if(stock==null||stock.signum()<=0)throw bad("Потрібен відомий додатний залишок. Спочатку проведіть інвентаризацію");
  if(((Number)p.get("version")).longValue()!=r.version()||stock.compareTo(r.stock())!=0||!Objects.equals(p.get("unit"),r.unit()))throw bad("Залишок або дані матеріалу змінилися. Оновіть дані й повторно перевірте вартість");
  if(!Set.of("METER","SQUARE_METER","PIECE").contains(r.unit()))throw bad("Одиниця матеріалу не налаштована");
  db.update("UPDATE parts SET inventory_unit_cost=?,inventory_cost_unit=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",r.unitCost(),r.unit(),id);
  var snapshot=new LinkedHashMap<String,Object>();snapshot.put("partNo",id);snapshot.put("description",p.get("description"));snapshot.put("stock",stock);snapshot.put("unit",r.unit());snapshot.put("unitCost",r.unitCost());snapshot.put("totalCost",stock.multiply(r.unitCost()));snapshot.put("note",r.note().trim());
  db.update("INSERT INTO opening_material_cost_events(request_key,part_no,actor,request_json,snapshot) VALUES(?,?,?,?,?)",r.requestKey(),id,a.getName(),encode(r),encode(snapshot));return snapshot;
 }
}
