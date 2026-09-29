package com.example.baget.stocktake;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.procurement.ProcurementRules;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class StocktakeService {
 private final JdbcTemplate db;private final UsersRepository users;private final InventoryReservations reservations;private final ObjectMapper json;
 public StocktakeService(JdbcTemplate db,UsersRepository users,InventoryReservations reservations,ObjectMapper json){this.db=db;this.users=users;this.reservations=reservations;this.json=json;}
 public record Create(@NotBlank @Size(max=36) String requestKey,@NotNull LocalDate date,@NotBlank @Size(max=1000) String note,@NotEmpty @Size(max=200) List<@NotNull @Positive Long> parts) {}
 public record Count(@Positive long partNo,@DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,@NotNull @Size(max=1000) String note) {}
 public record Save(@PositiveOrZero long expectedRevision,@NotNull LocalDate date,@NotBlank @Size(max=1000) String note,@NotEmpty @Size(max=200) List<@NotNull @Valid Count> lines) {}
 public record Revision(@PositiveOrZero long expectedRevision) {}
 public record Recount(@PositiveOrZero long expectedRevision,@NotBlank @Size(max=1000) String reason,@NotEmpty @Size(max=200) List<@NotNull @Positive Long> parts) {}
 public record Cancel(@PositiveOrZero long expectedRevision,@NotBlank @Size(max=1000) String reason) {}
 public record Post(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long expectedRevision,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String reviewToken,boolean acceptReservationReduction) {}
 public record Stamp(BigDecimal stock,Long version,String unit,String kind,long movement,long operation) {}
 public record Review(long revision,boolean canPost,boolean requiresApproval,List<Map<String,Object>> lines,String token) {}
 private void access(Authentication a){
  if(a==null||!a.isAuthenticated())throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
  if(!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE").contains(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"STOCKTAKE_ACCESS_DENIED","Немає права на інвентаризацію складу");
 }
 public List<Map<String,Object>> list(int page,Authentication a){access(a);return db.queryForList("SELECT id,revision,status,document_date AS date,note,actor,created_at AS createdAt,posted_at AS postedAt,posted_by AS postedBy FROM stocktake_documents ORDER BY id DESC LIMIT 50 OFFSET ?",Math.max(0,Math.min(page,100000))*50);}
 public Map<String,Object> read(long id,Authentication a){
  access(a);var doc=header(id,false);doc.remove("request_json");doc.remove("post_request");doc.remove("request_key");doc.remove("post_key");
  doc.put("lines",lines(id));doc.put("postedSnapshot",decode(doc.remove("posted_snapshot"),Review.class));
  doc.put("events",db.queryForList("SELECT revision,action,actor,snapshot,created_at AS createdAt FROM stocktake_events WHERE document_id=? ORDER BY id DESC LIMIT 200",id));return doc;
 }
 public Map<String,Object> create(Create r,Authentication a){
  access(a);uuid(r.requestKey());var previous=db.queryForList("SELECT id,actor,request_json FROM stocktake_documents WHERE request_key=?",r.requestKey());
  if(!previous.isEmpty()){sameRequest(previous.get(0).get("request_json"),r,previous.get(0).get("actor"),a);return read(number(previous.get(0),"id"),a);}
  var ids=unique(r.parts());reservations.lockParts(ids);var stamps=new TreeMap<Long,Stamp>();for(long p:ids)stamps.put(p,stamp(p));
  for(var st:stamps.values())unit(st);
  db.update("INSERT INTO stocktake_documents(request_key,request_json,document_date,note,actor) VALUES(?,?,?,?,?)",r.requestKey(),encode(r),r.date(),r.note().trim(),a.getName());
  long id=db.queryForObject("SELECT id FROM stocktake_documents WHERE request_key=?",Long.class,r.requestKey());
  for(long p:ids)addLine(id,p,stamps.get(p),null,"");event(id,0,"CREATE",Map.of("request",r,"lines",lines(id)),a);return read(id,a);
 }
 public Map<String,Object> save(long id,Save r,Authentication a){
  access(a);var doc=header(id,true);draft(doc,r.expectedRevision());
  var ids=unique(r.lines().stream().map(Count::partNo).toList());var old=lines(id);reservations.lockParts(ids);
  var existing=new HashMap<Long,Map<String,Object>>();for(var l:old)existing.put(number(l,"partNo"),l);
  for(var c:r.lines()){
   var st=stamp(c.partNo());unit(st);if(c.quantity()!=null)quantity(st.unit(),c.quantity());
   if(existing.containsKey(c.partNo())) {
    // Saving a count does not silently acknowledge intervening movements or change its baseline.
    db.update("UPDATE stocktake_lines SET counted_qty=?,note=? WHERE document_id=? AND part_no=?",c.quantity(),c.note().trim(),id,c.partNo());
   } else {if(c.quantity()!=null)throw conflict("Спочатку збережіть додавання нового матеріалу, потім внесіть його підрахунок");addLine(id,c.partNo(),st,null,c.note().trim());}
  }
  for(long p:existing.keySet())if(!ids.contains(p))db.update("DELETE FROM stocktake_lines WHERE document_id=? AND part_no=?",id,p);
  db.update("UPDATE stocktake_documents SET document_date=?,note=?,revision=revision+1 WHERE id=?",r.date(),r.note().trim(),id);
  event(id,r.expectedRevision()+1,"SAVE",Map.of("request",r,"lines",lines(id)),a);return read(id,a);
 }
 public Map<String,Object> recount(long id,Recount r,Authentication a){
  access(a);var doc=header(id,true);draft(doc,r.expectedRevision());var ids=unique(r.parts());
  var before=lines(id);var existing=new HashSet<Long>();for(var l:before)existing.add(number(l,"partNo"));
  if(!existing.containsAll(ids))throw conflict("У документі немає обраного матеріалу");
  reservations.lockParts(ids);var changes=new ArrayList<Object>();
  for(long p:ids){var st=stamp(p);unit(st);db.update("UPDATE stocktake_lines SET baseline=?,baseline_stock=?,unit=?,counted_qty=NULL WHERE document_id=? AND part_no=?",encode(st),st.stock(),st.unit(),id,p);changes.add(Map.of("partNo",p,"baseline",st));}
  bump(id);event(id,r.expectedRevision()+1,"RECOUNT",Map.of("reason",r.reason(),"before",before,"lines",changes),a);return read(id,a);
 }
 public Map<String,Object> cancel(long id,Cancel r,Authentication a){access(a);var doc=header(id,true);draft(doc,r.expectedRevision());db.update("UPDATE stocktake_documents SET status='CANCELLED',revision=revision+1 WHERE id=?",id);event(id,r.expectedRevision()+1,"CANCEL",r,a);return read(id,a);}
 public Review review(long id,Revision r,Authentication a){access(a);var doc=header(id,true);draft(doc,r.expectedRevision());var rows=lines(id);reservations.lockParts(rows.stream().map(l->number(l,"partNo")).toList());return buildReview(doc,rows);}
 private Review buildReview(Map<String,Object> doc,List<Map<String,Object>> saved){
  var result=new ArrayList<Map<String,Object>>();boolean valid=!saved.isEmpty(),approval=false;
  for(var l:saved){
   long p=number(l,"partNo");var st=stamp(p);var baseline=decode(l.get("baseline"),Stamp.class);var counted=decimal(l,"counted");
   boolean moved=!sameStamp(st,baseline);boolean configured="MATERIAL".equals(st.kind())&&Set.of("METER","SQUARE_METER","PIECE").contains(Objects.toString(st.unit(),""));
   var rs=reservationRows(p);List<StocktakeRules.Allocation> allocations=counted==null?List.of():StocktakeRules.allocate(counted,rs);
   var row=new LinkedHashMap<String,Object>();row.put("partNo",p);row.put("description",l.get("description"));row.put("unit",l.get("unit"));row.put("baseline",baseline);row.put("current",st);row.put("counted",counted);row.put("difference",StocktakeRules.difference(st.stock(),counted));row.put("note",l.get("note"));row.put("changed",moved);row.put("configured",configured);row.put("reservations",allocations);
   if(counted!=null && configured)quantity(st.unit(),counted);
   boolean reasonMissing=counted!=null&&(st.stock()==null||!StocktakeRules.same(st.stock(),counted))&&Objects.toString(l.get("note"),"").isBlank();
   row.put("reasonMissing",reasonMissing);
   if(moved||!configured||counted==null||reasonMissing)valid=false;
   if(allocations.stream().anyMatch(x->x.after().compareTo(x.before())<0))approval=true;
   result.add(row);
  }
  long revision=number(doc,"revision");String token=hash(encode(Map.of("documentId",doc.get("id"),"revision",revision,"lines",result)));
  return new Review(revision,valid,approval,result,token);
 }
 public Map<String,Object> post(long id,Post r,Authentication a){
  access(a);uuid(r.requestKey());var doc=header(id,true);
  if("POSTED".equals(doc.get("status"))){
   if(!r.requestKey().equals(doc.get("post_key")))throw conflict("Акт уже проведено; повторне проведення заборонене");
   sameRequest(doc.get("post_request"),r,doc.get("postedBy"),a);return read(id,a);
  }
  draft(doc,r.expectedRevision());var saved=lines(id);reservations.lockParts(saved.stream().map(l->number(l,"partNo")).toList());
  var review=buildReview(doc,saved);
  if(!review.canPost())throw conflict("Є непораховані позиції, відсутні причини розбіжностей або складські зміни. Перевірте акт і повторіть підрахунок змінених позицій");
  if(!review.token().equals(r.reviewToken()))throw conflict("Залишки або резерви змінилися після перегляду. Отримайте нову перевірку акта");
  if(review.requiresApproval()&&!r.acceptReservationReduction())throw conflict("Погодьте зменшення резервів за показаним переліком замовлень");
  for(var line:review.lines()){
   long p=number(line,"partNo");BigDecimal counted=decimal(line,"counted");var current=(Stamp)line.get("current");String key=UUID.randomUUID().toString();
   db.update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",counted,p);
   // Guard storage precision instead of silently writing an amount different from the signed-off count.
   var actual=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,p);
   if(!StocktakeRules.same(actual,counted))throw conflict("Склад не зберіг точну кількість. Перевірте застосування міграції V62");
   db.update("INSERT INTO material_stock_operations(request_key,part_no,operation,quantity,stock_before,stock_after,actor,note,request_json) VALUES(?,?,'COUNT',?,?,?,?,?,?)",key,p,counted,current.stock(),counted,a.getName(),"Акт інвентаризації № "+id,encode(Map.of("stocktakeId",id,"line",line)));
   db.update("INSERT INTO stocktake_posted_lines(document_id,part_no,stock_operation_key) VALUES(?,?,?)",id,p,key);
   @SuppressWarnings("unchecked") var allocations=(List<StocktakeRules.Allocation>)line.get("reservations");
   for(var allocation:allocations)if(!StocktakeRules.same(allocation.before(),allocation.after())){
    db.update("UPDATE material_reservations SET reserved=? WHERE id=?",allocation.after(),allocation.id());
    db.update("INSERT INTO reservation_events(reservation_id,reserved_before,reserved_after,reason,actor) VALUES(?,?,?,'STOCKTAKE',?)",allocation.id(),allocation.before(),allocation.after(),a.getName());
   }
  }
  db.update("UPDATE stocktake_documents SET status='POSTED',revision=revision+1,posted_at=CURRENT_TIMESTAMP(6),posted_by=?,post_key=?,post_request=?,posted_snapshot=? WHERE id=?",a.getName(),r.requestKey(),encode(r),encode(review),id);
  event(id,r.expectedRevision()+1,"POST",Map.of("review",review,"acceptedReservationReduction",r.acceptReservationReduction()),a);return read(id,a);
 }
 private List<StocktakeRules.Reservation> reservationRows(long part){
  return db.query("SELECT r.id,r.order_product_id,p.order_no,r.demand,r.reserved FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE r.part_no=? AND r.active=1 ORDER BY r.id",(rs,n)->new StocktakeRules.Reservation(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getBigDecimal(4),rs.getBigDecimal(5)),part);
 }
 private Stamp stamp(long part){
  var rows=db.query("SELECT CAST(p.OnHand AS DECIMAL(24,6)),p.Version,p.unit_type,p.part_kind,COALESCE((SELECT MAX(m.id) FROM inventory_movements m WHERE m.part_no=p.PartNo),0),COALESCE((SELECT MAX(o.id) FROM material_stock_operations o WHERE o.part_no=p.PartNo),0) FROM parts p WHERE p.PartNo=?",(rs,n)->new Stamp(rs.getBigDecimal(1),rs.getObject(2,Long.class),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getLong(6)),part);
  if(rows.isEmpty())throw conflict("Матеріал не знайдено");return rows.get(0);
 }
 static boolean sameStamp(Stamp a,Stamp b){return b!=null&&StocktakeRules.same(a.stock(),b.stock())&&Objects.equals(a.version(),b.version())&&Objects.equals(a.unit(),b.unit())&&Objects.equals(a.kind(),b.kind())&&a.movement()==b.movement()&&a.operation()==b.operation();}
 private void unit(Stamp st){if(!"MATERIAL".equals(st.kind())||!Set.of("METER","SQUARE_METER","PIECE").contains(Objects.toString(st.unit(),"")))throw conflict("Матеріал має бути налаштований як складський: м / м² / шт");}
 private void quantity(String unit,BigDecimal qty){try{ProcurementRules.quantity(unit,qty,true);}catch(IllegalArgumentException e){throw conflict(e.getMessage());}}
 private void addLine(long id,long part,Stamp stamp,BigDecimal count,String note){String description=db.queryForObject("SELECT Description FROM parts WHERE PartNo=?",String.class,part);db.update("INSERT INTO stocktake_lines(document_id,part_no,description,unit,baseline,baseline_stock,counted_qty,note) VALUES(?,?,?,?,?,?,?,?)",id,part,Objects.toString(description,""),stamp.unit(),encode(stamp),stamp.stock(),count,note);}
 private List<Map<String,Object>> lines(long id){return db.queryForList("SELECT part_no AS partNo,description,unit,baseline,baseline_stock AS baselineStock,counted_qty AS counted,note FROM stocktake_lines WHERE document_id=? ORDER BY part_no",id);}
 private Map<String,Object> header(long id,boolean lock){db.queryForObject("SELECT id FROM stocktake_documents WHERE id=?"+(lock?" FOR UPDATE":" FOR SHARE"),Long.class,id);return db.queryForMap("SELECT id,request_key,request_json,revision,status,document_date AS date,note,actor,created_at AS createdAt,posted_at AS postedAt,posted_by AS postedBy,post_key,post_request,posted_snapshot FROM stocktake_documents WHERE id=?",id);}
 private TreeSet<Long> unique(List<Long> ids){var result=new TreeSet<>(ids);if(result.size()!=ids.size()||result.isEmpty()||result.size()>200)throw conflict("Додайте від 1 до 200 різних матеріалів");return result;}
 private void draft(Map<String,Object> doc,long rev){if(!"DRAFT".equals(doc.get("status")))throw conflict("Змінювати можна лише чернетку акта");if(number(doc,"revision")!=rev)throw conflict("Акт змінено іншим користувачем. Відкрийте його повторно");}
 private void bump(long id){db.update("UPDATE stocktake_documents SET revision=revision+1 WHERE id=?",id);}
 private void event(long id,long revision,String action,Object data,Authentication a){db.update("INSERT INTO stocktake_events(document_id,revision,action,actor,snapshot) VALUES(?,?,?,?,?)",id,revision,action,a.getName(),encode(data));}
 private void sameRequest(Object old,Object request,Object actor,Authentication a){try{if(!a.getName().equals(actor)||!json.readTree(old.toString()).equals(json.readTree(encode(request))))throw conflict("Ключ операції вже використаний з іншими даними");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private <T>T decode(Object raw,Class<T> type){if(raw==null)return null;try{return json.readValue(raw.toString(),type);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void uuid(String key){try{UUID.fromString(key);}catch(IllegalArgumentException e){throw conflict("Некоректний ключ операції");}}
 private static long number(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 private static BigDecimal decimal(Map<String,Object> m,String k){return m.get(k)==null?null:new BigDecimal(m.get(k).toString());}
 private static PreviewException conflict(String message){return new PreviewException(HttpStatus.CONFLICT,"STOCKTAKE_CONFLICT",message);}
}
