package com.example.baget.receiptcost;
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
import java.math.*;
import java.time.LocalDate;
import java.util.*;
@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ReceiptCostService {
 private final JdbcTemplate db;private final UsersRepository users;private final InventoryReservations reservations;private final ObjectMapper json;
 public ReceiptCostService(JdbcTemplate db,UsersRepository users,InventoryReservations reservations,ObjectMapper json){this.db=db;this.users=users;this.reservations=reservations;this.json=json;}
 public record Line(@Positive long partNo,@NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,
  @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal unitPrice,
  @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=2) BigDecimal directCost,
  @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal openingUnitCost){}
 public record Request(@NotBlank @Size(max=36) String requestKey,@Positive long vendorNo,@NotBlank @Size(max=120) String documentNo,
  @NotNull LocalDate documentDate,@Positive Long supplierOrderId,@PositiveOrZero Long expectedRevision,
  @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=2) BigDecimal deliveryCost,
  @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=2) BigDecimal procurementCost,
  @NotNull @Size(max=1000) String note,@NotEmpty @Size(max=200) List<@NotNull @Valid Line> lines){}
 public record Post(@NotNull @Valid Request document,@NotBlank String reviewToken){}
 public record PricedLine(long partNo,String description,String unit,BigDecimal quantity,BigDecimal unitPrice,BigDecimal net,
  BigDecimal sharedCost,BigDecimal directCost,BigDecimal landed,BigDecimal receiptUnitCost,
  BigDecimal stockBefore,BigDecimal costBefore,boolean openingCostUsed,BigDecimal stockAfter,BigDecimal averageAfter,Long supplierLineId){}
 public record Preview(Request document,List<PricedLine> lines,BigDecimal purchaseTotal,BigDecimal expenseTotal,BigDecimal total,String token){}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE").contains(r.getName()))).orElse(false))throw bad("Немає доступу до приходу матеріалів",HttpStatus.FORBIDDEN);}
 public Object list(int page,Authentication a){access(a);return db.queryForList("SELECT r.id,r.document_no AS documentNo,r.document_date AS documentDate,r.supplier_order_id AS supplierOrderId,v.VendorName AS vendorName,r.actor,r.created_at AS createdAt FROM priced_material_receipts r JOIN vendors v ON v.VendorNo=r.vendor_no ORDER BY r.id DESC LIMIT 50 OFFSET ?",Math.max(0,Math.min(100000,page))*50);}
 public Object read(long id,Authentication a){access(a);var r=db.queryForMap("SELECT id,snapshot,actor,created_at AS createdAt FROM priced_material_receipts WHERE id=?",id);r.put("snapshot",tree(r.get("snapshot")));return r;}
 public Object byKey(String key,Authentication a){access(a);long id=db.queryForObject("SELECT id FROM priced_material_receipts WHERE request_key=?",Long.class,key);return read(id,a);}
 public Object material(long part,Authentication a){access(a);return db.queryForMap("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,CAST(OnHand AS DECIMAL(24,6)) AS stock,CASE WHEN inventory_cost_unit=unit_type THEN inventory_unit_cost ELSE NULL END AS averageCost FROM parts WHERE PartNo=? AND part_kind='MATERIAL'",part);}
 public Preview preview(Request r,Authentication a){access(a);return calculate(r,false);}
 private Preview calculate(Request r,boolean lock){
  try{UUID.fromString(r.requestKey());}catch(IllegalArgumentException e){throw bad("Некоректний ключ накладної");}
  if(db.queryForList("SELECT VendorNo FROM vendors WHERE VendorNo=?",Long.class,r.vendorNo()).isEmpty())throw bad("Оберіть постачальника");
  var supplied=new HashMap<Long,Map<String,Object>>();
  if(r.supplierOrderId()!=null){
   var order=db.queryForMap("SELECT vendor_no,status,revision FROM supplier_orders WHERE id=?"+(lock?" FOR UPDATE":""),r.supplierOrderId());
   if(number(order,"vendor_no")!=r.vendorNo() || r.expectedRevision()==null || number(order,"revision")!=r.expectedRevision() || !Set.of("ORDERED","PARTIAL").contains(order.get("status")))throw bad("Замовлення постачальнику змінено, закрито або належить іншому постачальнику");
   for(var l:db.queryForList("SELECT id,part_no,unit,ordered_qty,received_qty FROM supplier_order_lines WHERE order_id=?",r.supplierOrderId()))supplied.put(number(l,"part_no"),l);
  }
  if(lock)db.queryForObject("SELECT VendorNo FROM vendors WHERE VendorNo=? FOR UPDATE",Long.class,r.vendorNo());
  var sorted=r.lines().stream().sorted(Comparator.comparingLong(Line::partNo)).toList();var ids=new TreeSet<Long>();for(var l:sorted)if(!ids.add(l.partNo()))throw bad("Матеріал повторюється");
  if(lock)reservations.lockParts(ids);
  var nets=sorted.stream().map(l->ReceiptCostRules.money(l.quantity().multiply(l.unitPrice()))).toList();
  List<BigDecimal> allocated;try{allocated=ReceiptCostRules.allocate(nets,r.deliveryCost().add(r.procurementCost()));}catch(IllegalArgumentException e){throw bad(e.getMessage());}
  var result=new ArrayList<PricedLine>();BigDecimal expenses=r.deliveryCost().add(r.procurementCost()),total=BigDecimal.ZERO;
  for(int i=0;i<sorted.size();i++){
   var line=sorted.get(i);var p=db.queryForMap("SELECT Description,unit_type,CAST(OnHand AS DECIMAL(24,6)) AS stock,inventory_unit_cost,inventory_cost_unit FROM parts WHERE PartNo=? AND part_kind='MATERIAL'",line.partNo());
   String unit=Objects.toString(p.get("unit_type"),"");BigDecimal stock=decimal(p,"stock"),oldCost=unit.equals(p.get("inventory_cost_unit"))?decimal(p,"inventory_unit_cost"):null;
   try{ProcurementRules.quantity(unit,line.quantity(),false);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
   if(stock==null||stock.signum()<0)throw bad("Матеріал "+line.partNo()+": спочатку встановіть фактичний залишок інвентаризацією");
   boolean opening=stock.signum()>0&&oldCost==null;
   if(opening){oldCost=line.openingUnitCost();if(oldCost==null)throw bad("Матеріал "+line.partNo()+": вкажіть початкову собівартість наявного залишку");}
   else if(line.openingUnitCost()!=null)throw bad("Матеріал "+line.partNo()+": початкова собівартість вже не потрібна; оновіть картку матеріалу");
   Long supplierLine=null;
   if(r.supplierOrderId()!=null){var sl=supplied.get(line.partNo());if(sl==null || !unit.equals(sl.get("unit")))throw bad("Матеріал або одиниця не відповідає замовленню постачальнику");try{ProcurementRules.receive("ORDERED",decimal(sl,"ordered_qty"),decimal(sl,"received_qty"),line.quantity());}catch(IllegalArgumentException e){throw bad(e.getMessage());}supplierLine=number(sl,"id");}
   BigDecimal landed=nets.get(i).add(allocated.get(i)).add(line.directCost()),after=stock.add(line.quantity());
   try{ProcurementRules.quantity(unit,after,true);}catch(IllegalArgumentException e){throw bad("Залишок після приходу перевищує допустиму кількість");}
   BigDecimal avg;try{avg=ReceiptCostRules.average(stock,oldCost,line.quantity(),landed);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
   result.add(new PricedLine(line.partNo(),Objects.toString(p.get("Description"),""),unit,line.quantity(),line.unitPrice(),nets.get(i),allocated.get(i),line.directCost(),landed,landed.divide(line.quantity(),6,RoundingMode.HALF_UP),stock,oldCost,opening,after,avg,supplierLine));
   expenses=expenses.add(line.directCost());total=total.add(landed);
  }
  String token=hash(encode(Map.of("document",r,"lines",result)));
  return new Preview(r,List.copyOf(result),nets.stream().reduce(BigDecimal.ZERO,BigDecimal::add),expenses,total,token);
 }
 public Object post(Post request,Authentication a){
  access(a);var r=request.document();
  var prior=db.queryForList("SELECT id,request_json,actor FROM priced_material_receipts WHERE request_key=?",r.requestKey());
  if(!prior.isEmpty()){var old=prior.get(0);if(!a.getName().equals(old.get("actor"))||!tree(old.get("request_json")).equals(tree(encode(request))))throw bad("Ключ накладної вже використаний з іншими даними");return read(number(old,"id"),a);}
  var result=calculate(r,true);if(!result.token().equals(request.reviewToken()))throw bad("Дані змінилися після розрахунку. Перевірте накладну повторно");
  // Prevent charging an existing quantity-only supplier receipt for the second time.
  if(!db.queryForList("SELECT sr.id FROM supplier_receipts sr JOIN supplier_orders so ON so.id=sr.order_id WHERE so.vendor_no=? AND sr.document_no=? AND sr.received_date=?",Long.class,r.vendorNo(),r.documentNo().trim(),r.documentDate()).isEmpty())throw bad("Цю накладну вже проведено через постачання. Не створюйте повторний прихід");
  db.update("INSERT INTO priced_material_receipts(request_key,request_json,vendor_no,document_no,document_date,supplier_order_id,snapshot,actor) VALUES(?,?,?,?,?,?,?,?)",r.requestKey(),encode(request),r.vendorNo(),r.documentNo().trim(),r.documentDate(),r.supplierOrderId(),encode(result),a.getName());
  long id=db.queryForObject("SELECT id FROM priced_material_receipts WHERE request_key=?",Long.class,r.requestKey());Long sr=null;
  if(r.supplierOrderId()!=null){db.update("INSERT INTO supplier_receipts(order_id,request_key,request_json,document_no,received_date,note,actor) VALUES(?,?,?,?,?,?,?)",r.supplierOrderId(),r.requestKey(),encode(request),r.documentNo().trim(),r.documentDate(),r.note(),a.getName());sr=db.queryForObject("SELECT id FROM supplier_receipts WHERE request_key=?",Long.class,r.requestKey());db.update("UPDATE priced_material_receipts SET supplier_receipt_id=? WHERE id=?",sr,id);}
  for(var l:result.lines()){
   String key=UUID.randomUUID().toString();
   db.update("UPDATE parts SET OnHand=?,inventory_unit_cost=?,inventory_cost_unit=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",l.stockAfter(),l.averageAfter(),l.unit(),l.partNo());
   var actual=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,l.partNo());if(actual.compareTo(l.stockAfter())!=0)throw bad("Склад не зберіг точну кількість");
   db.update("INSERT INTO material_stock_operations(request_key,part_no,operation,quantity,stock_before,stock_after,actor,note,request_json) VALUES(?,?,'RECEIPT',?,?,?,?,?,?)",key,l.partNo(),l.quantity(),l.stockBefore(),l.stockAfter(),a.getName(),"Накладна "+r.documentNo()+" · прихід № "+id,encode(Map.of("pricedReceiptId",id,"line",l)));
   db.update("INSERT INTO priced_material_receipt_lines(receipt_id,part_no,stock_operation_key) VALUES(?,?,?)",id,l.partNo(),key);
   if(sr!=null){db.update("INSERT INTO supplier_receipt_lines(receipt_id,order_line_id,quantity,stock_operation_key) VALUES(?,?,?,?)",sr,l.supplierLineId(),l.quantity(),key);db.update("UPDATE supplier_order_lines SET received_qty=received_qty+? WHERE id=?",l.quantity(),l.supplierLineId());}
  }
  if(sr!=null){long remaining=db.queryForObject("SELECT COUNT(*) FROM supplier_order_lines WHERE order_id=? AND received_qty<ordered_qty",Long.class,r.supplierOrderId());db.update("UPDATE supplier_orders SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",remaining==0?"RECEIVED":"PARTIAL",r.supplierOrderId());db.update("INSERT INTO procurement_events(order_id,action,actor,snapshot) VALUES(?,'RECEIPT',?,?)",r.supplierOrderId(),a.getName(),encode(Map.of("receiptId",sr,"pricedReceiptId",id)));}
  for(var l:result.lines())reservations.refillPart(l.partNo(),a.getName());return read(id,a);
 }
 private String encode(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
 private com.fasterxml.jackson.databind.JsonNode tree(Object v){try{return json.readTree(v.toString());}catch(Exception e){throw new IllegalStateException(e);}}
 private String hash(String v){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static BigDecimal decimal(Map<String,Object> row,String key){return row.get(key)==null?null:new BigDecimal(row.get(key).toString());}
 private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private static PreviewException bad(String message){return bad(message,HttpStatus.CONFLICT);}
 private static PreviewException bad(String message,HttpStatus status){return new PreviewException(status,"PRICED_RECEIPT",message);}
}
