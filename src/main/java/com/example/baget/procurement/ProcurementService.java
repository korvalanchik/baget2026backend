package com.example.baget.procurement;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
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
public class ProcurementService {
    private final JdbcTemplate db;
    private final UsersRepository users;
    private final InventoryReservations reservations;
    private final ObjectMapper json;
    public ProcurementService(JdbcTemplate db,UsersRepository users,InventoryReservations reservations,ObjectMapper json) {
        this.db=db;this.users=users;this.reservations=reservations;this.json=json;
    }
    public record Line(@Positive long partNo,@NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity) {}
    public record Draft(@NotBlank @Size(max=36) String requestKey,@Positive long vendorNo,LocalDate expectedDate,
        @NotNull @Size(max=1000) String note,@NotEmpty @Size(max=200) List<@NotNull @Valid Line> lines) {}
    public record Edit(@PositiveOrZero long expectedRevision,@NotNull @Valid Draft draft) {}
    public record Action(@PositiveOrZero long expectedRevision,@NotBlank @Size(max=1000) String note) {}
    public record Schedule(@PositiveOrZero long expectedRevision,@NotNull LocalDate expectedDate,@NotBlank @Size(max=1000) String note) {}
    public record ReceivedLine(@Positive long lineId,@NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity) {}
    public record Receipt(@NotBlank @Size(max=36) String requestKey,@PositiveOrZero long expectedRevision,
        @NotBlank @Size(max=120) String documentNo,@NotNull LocalDate receivedDate,@NotNull @Size(max=1000) String note,
        @NotEmpty @Size(max=200) List<@NotNull @Valid ReceivedLine> lines) {}
    public record Policy(@PositiveOrZero long expectedRevision,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal minimum,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal target) {}
    private void access(Authentication a) {
        if(a==null || !a.isAuthenticated()) throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        boolean allowed=users.findByUsername(a.getName()).map(u->u.getRoles().stream()
            .anyMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE").contains(r.getName()))).orElse(false);
        if(!allowed) throw new PreviewException(HttpStatus.FORBIDDEN,"PROCUREMENT_ACCESS","Немає доступу до закупівлі матеріалів");
    }
    // Global warehouse scope, identical to WarehouseService. Supplier pipeline is derived solely from posted orders.
    private static final String NEEDS="""
        SELECT p.PartNo AS partNo,p.Description AS description,p.unit_type AS unit,p.VendorNo AS vendorNo,
        v.VendorName AS vendorName,CAST(p.OnHand AS DECIMAL(24,6)) AS onHand,
        COALESCE(r.demand,0) AS demand,COALESCE(r.reserved,0) AS reserved,
        COALESCE(i.incoming,0) AS incoming,i.expectedDate,i.overdue,
        COALESCE(s.minimum_qty,0) AS minimum,COALESCE(s.target_qty,0) AS target,
        COALESCE(s.revision,0) AS policyRevision,
        COALESCE(l.legacyOrdered,0) AS legacyOrdered,
        CASE WHEN p.calculation_method='FRAME_PROFILE' OR (p.InQuality=2 AND p.ProfilWidth>0.0003) THEN 1 ELSE 0 END AS frameProfile,
        COALESCE(fs.activeBranches,0) AS activeBranches,COALESCE(fp.status,'ALLOWED') AS purchaseStatus
        FROM parts p LEFT JOIN vendors v ON v.VendorNo=p.VendorNo
        LEFT JOIN (SELECT part_no,SUM(demand) AS demand,SUM(reserved) AS reserved
          FROM material_reservations WHERE active=1 GROUP BY part_no) r ON r.part_no=p.PartNo
        LEFT JOIN (SELECT l.part_no,SUM(l.ordered_qty-l.received_qty) AS incoming,
          MIN(o.expected_date) AS expectedDate,
          SUM(CASE WHEN o.expected_date<CURRENT_DATE THEN l.ordered_qty-l.received_qty ELSE 0 END) AS overdue
          FROM supplier_order_lines l JOIN supplier_orders o ON o.id=l.order_id
          WHERE o.status IN ('ORDERED','PARTIAL') AND l.received_qty<l.ordered_qty GROUP BY l.part_no) i ON i.part_no=p.PartNo
        LEFT JOIN material_stock_policy s ON s.part_no=p.PartNo
        LEFT JOIN (SELECT part_no,COUNT(*) AS activeBranches FROM branch_frame_samples WHERE status='ACTIVE' GROUP BY part_no) fs ON fs.part_no=p.PartNo
        LEFT JOIN frame_purchase_policy fp ON fp.part_no=p.PartNo
        LEFT JOIN (SELECT part_no,SUM(quantity) AS legacyOrdered FROM material_purchase_requests
          WHERE status='ORDERED' GROUP BY part_no) l ON l.part_no=p.PartNo
        WHERE p.part_kind='MATERIAL'
        """;
    public List<Map<String,Object>> needs(String q,int page,boolean onlyNeeded,Authentication a) {
        access(a);
        // Global StringTrimmerEditor(true) converts an empty search parameter to null.
        String search=q==null?"":q.trim();
        // Filtering is in SQL before paging. Unknown stock needs a count, not an invented zero balance.
        String filter=onlyNeeded?" AND (p.OnHand IS NULL OR COALESCE(r.demand,0)>COALESCE(r.reserved,0) OR p.OnHand+COALESCE(i.incoming,0)-COALESCE(r.demand,0)<CASE WHEN (p.calculation_method='FRAME_PROFILE' OR (p.InQuality=2 AND p.ProfilWidth>0.0003)) AND COALESCE(fs.activeBranches,0)=0 THEN 0 ELSE COALESCE(s.minimum_qty,0) END)":"";
        var rows=db.queryForList(NEEDS+" AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0)"+filter+" ORDER BY p.PartNo LIMIT 100 OFFSET ?",search,search,Math.max(0,Math.min(page,100000))*100);
        rows.forEach(this::calculateNeed);return rows;
    }
    private void calculateNeed(Map<String,Object> row) {
        boolean frame=((Number)row.get("frameProfile")).intValue()==1;
        long active=((Number)row.get("activeBranches")).longValue();
        var need=ProcurementRules.assortmentNeed(decimal(row,"onHand"),decimal(row,"demand"),decimal(row,"reserved"),decimal(row,"incoming"),decimal(row,"minimum"),decimal(row,"target"),frame,active);
        row.put("replenishStock",!frame || active>0);
        row.put("purchaseBlocked",!"ALLOWED".equals(row.get("purchaseStatus")));
        row.put("requiredPurchase",need.recommended());
        row.put("free",need.free());row.put("shortage",need.shortage());row.put("projectedFree",need.projectedFree());
        row.put("low",need.low());row.put("recommended",Boolean.TRUE.equals(row.get("purchaseBlocked"))?BigDecimal.ZERO:need.recommended());
    }
    public Map<String,Object> need(long part,Authentication a) {
        access(a);var row=db.queryForMap(NEEDS+" AND p.PartNo=?",part);calculateNeed(row);
        row.put("reservations",db.queryForList("SELECT p.order_no AS orderNo,r.order_product_id AS productId,r.demand,r.reserved FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE r.part_no=? AND r.active=1 ORDER BY r.id",part));
        row.put("deliveries",db.queryForList("SELECT o.id,o.expected_date AS expectedDate,v.VendorName AS vendorName,l.ordered_qty-l.received_qty AS quantity FROM supplier_order_lines l JOIN supplier_orders o ON o.id=l.order_id JOIN vendors v ON v.VendorNo=o.vendor_no WHERE l.part_no=? AND o.status IN ('ORDERED','PARTIAL') AND l.received_qty<l.ordered_qty ORDER BY o.expected_date,o.id",part));
        return row;
    }
    public Map<String,Object> policy(long part,Policy r,Authentication a) {
        access(a);var p=part(part,true);quantity(p,r.minimum(),true);quantity(p,r.target(),true);
        if(r.target().compareTo(r.minimum())<0) throw conflict("Бажаний запас не може бути меншим за мінімальний");
        var rows=db.queryForList("SELECT revision FROM material_stock_policy WHERE part_no=?",part);
        long revision=rows.isEmpty()?0:number(rows.get(0),"revision");
        if(revision!=r.expectedRevision()) throw conflict("Налаштування запасу змінились. Оновіть дані");
        db.update("INSERT INTO material_stock_policy(part_no,minimum_qty,target_qty,revision,actor) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE minimum_qty=VALUES(minimum_qty),target_qty=VALUES(target_qty),revision=VALUES(revision),actor=VALUES(actor),updated_at=CURRENT_TIMESTAMP(6)",part,r.minimum(),r.target(),revision+1,a.getName());
        event(null,part,"POLICY",r,a);return need(part,a);
    }
    public List<Map<String,Object>> vendors(Authentication a) {
        access(a);return db.queryForList("SELECT VendorNo AS id,VendorName AS name FROM vendors ORDER BY VendorName,VendorNo");
    }
    public List<Map<String,Object>> list(int page,Authentication a) {
        access(a);return db.queryForList("SELECT o.id,o.vendor_no AS vendorNo,v.VendorName AS vendorName,o.status,o.revision,o.expected_date AS expectedDate,o.created_at AS createdAt FROM supplier_orders o JOIN vendors v ON v.VendorNo=o.vendor_no ORDER BY o.id DESC LIMIT 100 OFFSET ?",Math.max(0,Math.min(page,100000))*100);
    }
    public Map<String,Object> read(long id,Authentication a) {
        access(a);var o=header(id,false);
        o.put("lines",lines(id));
        o.put("receipts",db.queryForList("SELECT id,document_no AS documentNo,received_date AS receivedDate,note,actor,created_at AS createdAt FROM supplier_receipts WHERE order_id=? ORDER BY id DESC",id));
        o.put("receiptLines",db.queryForList("SELECT r.id AS receiptId,l.order_line_id AS lineId,l.quantity FROM supplier_receipt_lines l JOIN supplier_receipts r ON r.id=l.receipt_id WHERE r.order_id=? ORDER BY r.id,l.order_line_id",id));
        o.put("events",db.queryForList("SELECT action,actor,snapshot,created_at AS createdAt FROM procurement_events WHERE order_id=? ORDER BY id DESC",id));return o;
    }
    public Map<String,Object> create(Draft r,Authentication a) {
        access(a);uuid(r.requestKey());
        var old=db.queryForList("SELECT id,actor,request_json FROM supplier_orders WHERE request_key=?",r.requestKey());
        if(!old.isEmpty()) {same(old.get(0),r,a);return read(number(old.get(0),"id"),a);}
        var parts=validateDraft(r);
        db.update("INSERT INTO supplier_orders(request_key,request_json,vendor_no,expected_date,note,actor) VALUES(?,?,?,?,?,?)",r.requestKey(),encode(r),r.vendorNo(),r.expectedDate(),r.note(),a.getName());
        long id=db.queryForObject("SELECT id FROM supplier_orders WHERE request_key=?",Long.class,r.requestKey());
        insertLines(id,r,parts);event(id,null,"CREATE",r,a);return read(id,a);
    }
    public Map<String,Object> edit(long id,Edit r,Authentication a) {
        access(a);var o=header(id,true);revision(o,r.expectedRevision());
        if(!"DRAFT".equals(o.get("status"))) throw conflict("Редагувати можна лише чернетку");
        uuid(r.draft().requestKey());var parts=validateDraft(r.draft());
        db.update("UPDATE supplier_orders SET vendor_no=?,expected_date=?,note=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",r.draft().vendorNo(),r.draft().expectedDate(),r.draft().note(),id);
        db.update("DELETE FROM supplier_order_lines WHERE order_id=?",id);insertLines(id,r.draft(),parts);
        event(id,null,"EDIT",r,a);return read(id,a);
    }
    public Map<String,Object> place(long id,Action r,Authentication a) {
        access(a);var o=header(id,true);revision(o,r.expectedRevision());
        if(!"DRAFT".equals(o.get("status"))) throw conflict("Підтвердити можна лише чернетку");
        if(o.get("expectedDate")==null) throw conflict("Вкажіть очікувану дату поставки в чернетці");
        var lines=lines(id);
        if(lines.isEmpty()) throw conflict("Додайте матеріали");
        reservations.lockParts(lines.stream().map(l->number(l,"partNo")).toList());
        for(var l:lines) {var p=part(number(l,"partNo"),false);purchasable(number(l,"partNo"));quantity(p,decimal(l,"ordered"),false);if(!Objects.equals(p.get("unit"),l.get("unit")))throw conflict("Одиниця матеріалу змінилась. Перезбережіть чернетку");}
        state(id,"ORDERED");event(id,null,"ORDERED",r,a);return read(id,a);
    }
    public Map<String,Object> reschedule(long id,Schedule r,Authentication a) {
        access(a);var o=header(id,true);revision(o,r.expectedRevision());
        if(!Set.of("ORDERED","PARTIAL").contains(o.get("status"))) throw conflict("Перенести дату можна лише для відкритої поставки");
        db.update("UPDATE supplier_orders SET expected_date=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",r.expectedDate(),id);
        event(id,null,"RESCHEDULE",r,a);return read(id,a);
    }
    public Map<String,Object> close(long id,Action r,Authentication a) {
        access(a);var o=header(id,true);revision(o,r.expectedRevision());
        String status=o.get("status").toString();
        if(!Set.of("DRAFT","ORDERED","PARTIAL").contains(status)) throw conflict("Замовлення вже закрите");
        state(id,"PARTIAL".equals(status)?"CLOSED":"CANCELLED");event(id,null,"CLOSE_REMAINDER",r,a);return read(id,a);
    }
    public Map<String,Object> receive(long id,Receipt r,Authentication a) {
        access(a);uuid(r.requestKey());var o=header(id,true);
        var prior=db.queryForList("SELECT order_id,actor,request_json FROM supplier_receipts WHERE request_key=?",r.requestKey());
        if(!prior.isEmpty()) {
            if(number(prior.get(0),"order_id")!=id) throw conflict("Ключ приходу належить іншому замовленню");
            same(prior.get(0),r,a);return read(id,a);
        }
        revision(o,r.expectedRevision());
        var selected=new LinkedHashMap<Long,BigDecimal>();
        for(var l:r.lines()) if(selected.put(l.lineId(),l.quantity())!=null) throw conflict("Дубль позиції приходу");
        var saved=new HashMap<Long,Map<String,Object>>();for(var l:lines(id))saved.put(number(l,"id"),l);
        var partIds=new TreeSet<Long>();
        for(var e:selected.entrySet()) {
            var l=saved.get(e.getKey());if(l==null)throw conflict("Позиція не належить цьому замовленню");
            try {ProcurementRules.receive(o.get("status").toString(),decimal(l,"ordered"),decimal(l,"received"),e.getValue());}catch(IllegalArgumentException ex){throw conflict(ex.getMessage());}
            partIds.add(number(l,"partNo"));
        }
        reservations.lockParts(partIds);
        var parts=new HashMap<Long,Map<String,Object>>();
        // Validate every line before any write. Null stock must be established by an inventory count first.
        for(var e:selected.entrySet()) {
            var l=saved.get(e.getKey());long pid=number(l,"partNo");var p=part(pid,false);quantity(p,e.getValue(),false);
            if(!Objects.equals(p.get("unit"),l.get("unit"))) throw conflict("Одиниця матеріалу змінилась; потрібне узгодження замовлення");
            if(p.get("stock")==null || decimal(p,"stock").signum()<0) throw conflict("Спочатку встановіть фактичний залишок матеріалу "+pid+" через інвентаризацію");
            parts.put(pid,p);
        }
        db.update("INSERT INTO supplier_receipts(order_id,request_key,request_json,document_no,received_date,note,actor) VALUES(?,?,?,?,?,?,?)",id,r.requestKey(),encode(r),r.documentNo().trim(),r.receivedDate(),r.note(),a.getName());
        long receipt=db.queryForObject("SELECT id FROM supplier_receipts WHERE request_key=?",Long.class,r.requestKey());
        for(var e:selected.entrySet()) {
            var l=saved.get(e.getKey());long pid=number(l,"partNo");var before=decimal(parts.get(pid),"stock");var after=before.add(e.getValue());
            String key=UUID.randomUUID().toString();
            db.update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",after,pid);
            // Same stock history as single-position receiving; supplier references are explicit, not parsed from text.
            db.update("INSERT INTO material_stock_operations(request_key,part_no,operation,quantity,stock_before,stock_after,actor,note,request_json) VALUES(?,?,'RECEIPT',?,?,?,?,?,?)",key,pid,e.getValue(),before,after,a.getName(),"Поставка № "+id+", накладна "+r.documentNo().trim(),encode(Map.of("supplierOrderId",id,"receiptId",receipt,"quantity",e.getValue())));
            db.update("INSERT INTO supplier_receipt_lines(receipt_id,order_line_id,quantity,stock_operation_key) VALUES(?,?,?,?)",receipt,e.getKey(),e.getValue(),key);
            db.update("UPDATE supplier_order_lines SET received_qty=received_qty+? WHERE id=? AND order_id=?",e.getValue(),e.getKey(),id);
        }
        long remaining=db.queryForObject("SELECT COUNT(*) FROM supplier_order_lines WHERE order_id=? AND received_qty<ordered_qty",Long.class,id);
        state(id,remaining==0?"RECEIVED":"PARTIAL");event(id,null,"RECEIPT",Map.of("receiptId",receipt,"request",r),a);
        for(long p:partIds) reservations.refillPart(p,a.getName());
        return read(id,a);
    }
    private Map<Long,Map<String,Object>> validateDraft(Draft r) {
        if(db.queryForList("SELECT VendorNo FROM vendors WHERE VendorNo=?",Long.class,r.vendorNo()).isEmpty())throw conflict("Оберіть постачальника з довідника");
        var ids=new TreeSet<Long>();for(var l:r.lines())if(!ids.add(l.partNo()))throw conflict("Матеріал повторюється в замовленні");
        reservations.lockParts(ids);var result=new HashMap<Long,Map<String,Object>>();
        for(var l:r.lines()){var p=part(l.partNo(),false);purchasable(l.partNo());quantity(p,l.quantity(),false);result.put(l.partNo(),p);}return result;
    }
    private void purchasable(long part) {
        var statuses=db.queryForList("SELECT status FROM frame_purchase_policy WHERE part_no=?",String.class,part);
        if(!statuses.isEmpty() && !"ALLOWED".equals(statuses.get(0)))throw conflict("Закупівлю профілю "+part+" заборонено або його знято з виробництва. Узгодьте заміну");
    }
    private void insertLines(long id,Draft r,Map<Long,Map<String,Object>> parts) {
        for(var l:r.lines()){var p=parts.get(l.partNo());db.update("INSERT INTO supplier_order_lines(order_id,part_no,description,unit,ordered_qty) VALUES(?,?,?,?,?)",id,l.partNo(),Objects.toString(p.get("description"),""),p.get("unit"),l.quantity());}
    }
    private Map<String,Object> part(long id,boolean lock) {
        return db.queryForMap("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,CAST(OnHand AS DECIMAL(24,6)) AS stock FROM parts WHERE PartNo=? AND part_kind='MATERIAL'"+(lock?" FOR UPDATE":""),id);
    }
    private Map<String,Object> header(long id,boolean lock) {
        db.queryForObject("SELECT id FROM supplier_orders WHERE id=?"+(lock?" FOR UPDATE":" FOR SHARE"),Long.class,id);
        return db.queryForMap("SELECT o.id,o.vendor_no AS vendorNo,v.VendorName AS vendorName,o.status,o.revision,o.expected_date AS expectedDate,o.note,o.actor,o.created_at AS createdAt FROM supplier_orders o JOIN vendors v ON v.VendorNo=o.vendor_no WHERE o.id=?",id);
    }
    private List<Map<String,Object>> lines(long id) {
        return db.queryForList("SELECT id,part_no AS partNo,description,unit,ordered_qty AS ordered,received_qty AS received,ordered_qty-received_qty AS remaining FROM supplier_order_lines WHERE order_id=? ORDER BY id",id);
    }
    private void state(long id,String state) {db.update("UPDATE supplier_orders SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",state,id);}
    private void revision(Map<String,Object> row,long expected) {if(number(row,"revision")!=expected)throw conflict("Документ змінено. Відкрийте його повторно");}
    private void event(Long order,Long part,String action,Object snapshot,Authentication a) {db.update("INSERT INTO procurement_events(order_id,part_no,action,actor,snapshot) VALUES(?,?,?,?,?)",order,part,action,a.getName(),encode(snapshot));}
    private void quantity(Map<String,Object> p,BigDecimal qty,boolean zero) {try{ProcurementRules.quantity((String)p.get("unit"),qty,zero);}catch(IllegalArgumentException e){throw conflict(e.getMessage());}}
    private void same(Map<String,Object> old,Object request,Authentication a) {
        try {if(!a.getName().equals(old.get("actor")) || !json.readTree(old.get("request_json").toString()).equals(json.readTree(encode(request))))throw conflict("Ключ операції вже використаний з іншими даними");}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private void uuid(String key) {try{UUID.fromString(key);}catch(IllegalArgumentException e){throw conflict("Некоректний ключ операції");}}
    private String encode(Object o) {try{return json.writeValueAsString(o);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static BigDecimal decimal(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:new BigDecimal(v.toString());}
    private static PreviewException conflict(String message){return new PreviewException(HttpStatus.CONFLICT,"PROCUREMENT_CONFLICT",message);}
}
