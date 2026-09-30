package com.example.baget.warehouse;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
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
public class WarehouseService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.baget.finance.ProductPaymentCheck paymentCheck;
    private final JdbcTemplate db;
    private final UsersRepository users;
    private final InventoryReservations reservations;
    private final ObjectMapper json;
    private final com.example.baget.orderproduct.ReworkService reworks;
    public WarehouseService(JdbcTemplate db,UsersRepository users,InventoryReservations reservations,ObjectMapper json,com.example.baget.orderproduct.ReworkService reworks) {
        this.db=db;this.users=users;this.reservations=reservations;this.json=json;this.reworks=reworks;
    }
    private Set<String> roles(Authentication a) {
        if(a==null || !a.isAuthenticated()) throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        return users.findByUsername(a.getName()).orElseThrow(()->denied()).getRoles().stream().map(r->r.getName()).collect(java.util.stream.Collectors.toSet());
    }
    private void stockAccess(Authentication a) {
        if(Collections.disjoint(roles(a),Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE"))) throw denied();
    }
    private void productAccess(Map<String,Object> p,Authentication a) {
        var roles=roles(a);
        if(Collections.disjoint(roles,Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE","ROLE_MANAGER","ROLE_SELLER","ROLE_COUNTER","ROLE_LEVEL2","ROLE_USER"))) throw denied();
        if(!roles.contains("ROLE_ADMIN") && users.findByUsername(a.getName()).orElseThrow().getAllowedBranches().stream().noneMatch(b->b.getName().equals(p.get("branchName")))) throw denied();
    }
    private Map<String,Object> product(String token,boolean lock) {
        var rows=db.queryForList("SELECT p.id,p.order_no AS orderNo,o.StatusOrder AS orderStatus,p.client_product_id AS productName,p.production_state AS state,p.production_confirmed AS confirmed,p.current_location AS location,p.pending_location AS pending,p.tracking_revision AS revision,p.tracking_id AS trackingId,p.transfer_actor AS transferActor,p.warehouse_received_at AS warehouseReceivedAt,b.BranchName AS branchName FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE p.tracking_id=? AND p.active=1"+(lock?" FOR UPDATE":""),token);
        if(rows.isEmpty()) throw new PreviewException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Виріб не знайдено");
        return rows.get(0);
    }
    public Map<String,Object> tracking(String token,Authentication a) {
        var p=product(token,false);productAccess(p,a);
        p.put("history",db.queryForList("SELECT action,from_location AS fromLocation,to_location AS toLocation,actor,note,created_at AS createdAt FROM product_transfers WHERE product_id=? ORDER BY id DESC",p.get("id")));
        p.put("materials",db.queryForList("SELECT m.Description AS description,i.calculation_role AS role FROM items i JOIN parts m ON m.PartNo=i.PartNo WHERE i.order_product_id=? ORDER BY i.ItemNo",p.get("id")));
        var r=roles(a);p.put("canCancel",p.get("pending")!=null && (a.getName().equals(p.get("transferActor")) || r.contains("ROLE_ADMIN")));p.put("canWarehouse",!Collections.disjoint(r,Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE")));
        p.put("canReceiveWorkshop",!Collections.disjoint(r,Set.of("ROLE_ADMIN","ROLE_FOREMAN")));
        p.put("canReturnWorkshop",Set.of("WAREHOUSE","BRANCH").contains(p.get("location"))
            && !reworks.invoiced(((Number)p.get("orderNo")).longValue(),((Number)p.get("id")).longValue()));
        p.put("payment",paymentCheck.read(((Number)p.get("id")).longValue()));
        return p;
    }
    public String trackingForProduct(long pid,Authentication a) {
        var tokens=db.queryForList("SELECT tracking_id FROM order_products WHERE id=? AND active=1",String.class,pid);
        if(tokens.isEmpty()) throw conflict("Виріб не знайдено");
        tracking(tokens.get(0),a);return tokens.get(0);
    }
    public List<Map<String,Object>> queue(int page,Authentication a) {
        stockAccess(a);
        var u=users.findByUsername(a.getName()).orElseThrow();
        var branches=u.getAllowedBranches().stream().map(b->b.getName()).toList();
        boolean admin=roles(a).contains("ROLE_ADMIN");
        if(!admin && branches.isEmpty()) return List.of();
        var params=new ArrayList<Object>();
        String scope="";
        if(!admin){scope=" AND b.BranchName IN ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")";params.addAll(branches);}
        params.add(Math.max(0,Math.min(page,100000))*100);
        return db.queryForList("SELECT p.tracking_id AS trackingId,p.order_no AS orderNo,p.client_product_id AS productName,p.current_location AS location,p.pending_location AS pending,b.BranchName AS branchName FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE p.active=1 AND p.production_confirmed=1 AND p.current_location<>'CUSTOMER'"+scope+" ORDER BY p.id LIMIT 100 OFFSET ?",params.toArray());
    }
    public record Transfer(@PositiveOrZero long expectedRevision,@NotBlank String destination,@Size(max=1000) String note) {}
    public Map<String,Object> transfer(String token,Transfer r,boolean accept,Authentication a) {
        var initial=product(token,false);productAccess(initial,a);
        Long paymentInvoice="CUSTOMER".equals(r.destination())?paymentCheck.lock(((Number)initial.get("id")).longValue()):null;
        db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,initial.get("orderNo"));
        var p=product(token,true);productAccess(p,a);
        if(((Number)p.get("revision")).longValue()!=r.expectedRevision()) throw conflict("Картка змінилася. Оновіть її");
        if(!"COMPLETED".equals(p.get("state"))) throw conflict("Спочатку завершіть виріб у майстерні");
        String from=(String)p.get("location"),to=r.destination();
        String pending=(String)p.get("pending");
        Map<String,Object> creditSnapshot=Map.of();
        if("CUSTOMER".equals(to)) {
            if(p.get("orderStatus") instanceof Number status && Set.of(5,10).contains(status.intValue())) throw conflict("Скасоване замовлення не можна видати клієнту");
            creditSnapshot=paymentCheck.checkIssue(((Number)p.get("id")).longValue(),paymentInvoice);
        }
        if("WORKSHOP".equals(to)) {
            if(reworks.invoiced(((Number)p.get("orderNo")).longValue(),((Number)p.get("id")).longValue()))
                throw conflict("За виробом уже оформлено рахунок. Повернення після рахунку потребує фінансового коригування");
            if(!accept && (r.note()==null || r.note().isBlank()))throw conflict("Вкажіть причину повернення до майстерні");
            if(accept && Collections.disjoint(roles(a),Set.of("ROLE_ADMIN","ROLE_FOREMAN")))throw denied();
        }
        if(accept) {
            if(pending==null || !pending.equals(to)) throw conflict("Немає такої передачі для приймання");
            if("WAREHOUSE".equals(to)) {
                stockAccess(a);
                db.update("UPDATE order_products SET warehouse_received_at=COALESCE(warehouse_received_at,CURRENT_TIMESTAMP(6)) WHERE id=?",p.get("id"));
            }
            if("WORKSHOP".equals(to))db.update("UPDATE order_products SET warehouse_received_at=NULL WHERE id=?",p.get("id"));
            db.update("UPDATE order_products SET current_location=?,pending_location=NULL,transfer_actor=NULL,tracking_revision=tracking_revision+1 WHERE id=?",to,p.get("id"));
        } else {
            if(pending!=null) throw conflict("Попередню передачу ще не прийнято");
            if(!WarehouseRules.route(from,to)) throw conflict("Недопустимий маршрут передачі");
            if(!"BRANCH".equals(from)) stockAccess(a);
            db.update("UPDATE order_products SET pending_location=?,transfer_actor=?,tracking_revision=tracking_revision+1 WHERE id=?",to,a.getName(),p.get("id"));
        }
        if("CUSTOMER".equals(to))paymentCheck.recordIssue(((Number)p.get("id")).longValue(),r.expectedRevision()+1,accept,a.getName(),creditSnapshot);
        db.update("INSERT INTO product_transfers(product_id,action,from_location,to_location,actor,note) VALUES(?,?,?,?,?,?)",p.get("id"),accept?"ACCEPT":"SEND",from,to,a.getName(),r.note());
        return tracking(token,a);
    }
    public Map<String,Object> cancelTransfer(String token,Transfer r,Authentication a) {
        var initial=product(token,false);productAccess(initial,a);
        db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,initial.get("orderNo"));
        var p=product(token,true);productAccess(p,a);
        if(((Number)p.get("revision")).longValue()!=r.expectedRevision() || p.get("pending")==null) throw conflict("Передачу вже змінено або прийнято");
        if(!a.getName().equals(p.get("transferActor")) && !roles(a).contains("ROLE_ADMIN")) throw denied();
        if(r.note()==null || r.note().isBlank()) throw conflict("Вкажіть причину відкликання передачі");
        db.update("INSERT INTO product_transfers(product_id,action,from_location,to_location,actor,note) VALUES(?,'CANCEL',?,?,?,?)",p.get("id"),p.get("location"),p.get("pending"),a.getName(),r.note());
        db.update("UPDATE order_products SET pending_location=NULL,transfer_actor=NULL,tracking_revision=tracking_revision+1 WHERE id=?",p.get("id"));
        return tracking(token,a);
    }
    public List<Map<String,Object>> materials(String search,int page,boolean shortageOnly,Authentication a) {
        stockAccess(a);
        return db.queryForList("SELECT p.PartNo AS partNo,p.Description AS description,p.unit_type AS unit,CAST(p.OnHand AS DECIMAL(24,6)) AS onHand,COALESCE(r.demand,0) AS demand,COALESCE(r.reserved,0) AS reserved,GREATEST(0,COALESCE(r.demand,0)-COALESCE(r.reserved,0)) AS shortage FROM parts p LEFT JOIN (SELECT part_no,SUM(demand) demand,SUM(reserved) reserved FROM material_reservations WHERE active=1 GROUP BY part_no) r ON r.part_no=p.PartNo WHERE (p.part_kind='MATERIAL' OR r.part_no IS NOT NULL) AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0) AND (?=0 OR COALESCE(r.demand,0)>COALESCE(r.reserved,0)) ORDER BY p.PartNo LIMIT 100 OFFSET ?",search,search,shortageOnly?1:0,Math.max(0,Math.min(page,100000))*100);
    }
    public Map<String,Object> material(long id,Authentication a) {
        stockAccess(a);
        var p=db.queryForMap("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,CAST(OnHand AS DECIMAL(24,6)) AS onHand FROM parts WHERE PartNo=? AND part_kind='MATERIAL'",id);
        p.put("reservations",db.queryForList("SELECT r.order_product_id AS productId,r.demand,r.reserved,p.order_no AS orderNo FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE r.part_no=? AND r.active=1 ORDER BY r.id",id));
        p.put("history",db.queryForList("SELECT operation,quantity,stock_before AS stockBefore,stock_after AS stockAfter,actor,note,created_at AS createdAt FROM material_stock_operations WHERE part_no=? ORDER BY id DESC LIMIT 100",id));
        p.put("consumption",db.queryForList("SELECT order_no AS orderNo,item_no AS itemNo,quantity_delta AS quantity,actor,created_at AS createdAt FROM inventory_movements WHERE part_no=? ORDER BY id DESC LIMIT 100",id));
        p.put("requests",db.queryForList("SELECT id,quantity,status,revision,actor,note,created_at AS createdAt FROM material_purchase_requests WHERE part_no=? ORDER BY id DESC LIMIT 100",id));
        p.put("requestEvents",db.queryForList("SELECT e.request_id AS requestId,e.status,e.actor,e.created_at AS createdAt FROM material_request_events e JOIN material_purchase_requests r ON r.id=e.request_id WHERE r.part_no=? ORDER BY e.id DESC LIMIT 100",id));
        return p;
    }
    public record Stock(@NotBlank @Size(max=36) String requestKey,@NotBlank String operation,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,
        @Digits(integer=18,fraction=6) BigDecimal expectedStock,@NotBlank @Size(max=1000) String note) {}
    public Map<String,Object> stock(long id,Stock r,Authentication a) {
        stockAccess(a);uuid(r.requestKey());
        var part=db.queryForMap("SELECT CAST(OnHand AS DECIMAL(24,6)) AS stock,unit_type AS unit FROM parts WHERE PartNo=? AND part_kind='MATERIAL' FOR UPDATE",id);
        var old=db.queryForList("SELECT part_no,actor,request_json FROM material_stock_operations WHERE request_key=?",r.requestKey());
        if(!old.isEmpty()) {
            try {
                var prior=json.readValue(old.get(0).get("request_json").toString(),Stock.class);
                if(((Number)old.get(0).get("part_no")).longValue()!=id || !a.getName().equals(old.get(0).get("actor"))
                    || !r.operation().equals(prior.operation()) || !r.note().equals(prior.note())
                    || !WarehouseRules.same(r.quantity(),prior.quantity()) || !WarehouseRules.same(r.expectedStock(),prior.expectedStock()))
                    throw conflict("Ключ операції вже використано");
            }
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
            return material(id,a);
        }
        BigDecimal before=(BigDecimal)part.get("stock");
        if(!WarehouseRules.same(before,r.expectedStock())) throw conflict("Залишок змінився. Оновіть матеріал і повторіть перевірку");
        if("PIECE".equals(part.get("unit")) && r.quantity().stripTrailingZeros().scale()>0) throw conflict("Штучні матеріали потребують цілої кількості");
        BigDecimal after;
        try {after=WarehouseRules.stockAfter(before,r.operation(),r.quantity());}catch(IllegalArgumentException e){throw conflict(e.getMessage());}
        var reserved=db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1",BigDecimal.class,id);
        if(after.compareTo(reserved)<0) throw conflict("Фактичний залишок менший за резерв. Спочатку узгодьте резерви; інвентаризація не скасовує їх автоматично");
        db.update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",after,id);
        db.update("INSERT INTO material_stock_operations(request_key,part_no,operation,quantity,stock_before,stock_after,actor,note,request_json) VALUES(?,?,?,?,?,?,?,?,?)",r.requestKey(),id,r.operation(),r.quantity(),before,after,a.getName(),r.note(),json.valueToTree(r).toString());
        reservations.refillPart(id,a.getName());
        return material(id,a);
    }
    public record Purchase(@NotBlank @Size(max=36) String requestKey,@NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,@NotBlank @Size(max=1000) String note) {}
    public Map<String,Object> request(long id,Purchase r,Authentication a) {
        stockAccess(a);uuid(r.requestKey());
        String unit=db.queryForObject("SELECT unit_type FROM parts WHERE PartNo=? AND part_kind='MATERIAL' FOR UPDATE",String.class,id);
        if("PIECE".equals(unit) && r.quantity().stripTrailingZeros().scale()>0) throw conflict("Вкажіть цілу кількість");
        var old=db.queryForList("SELECT part_no,quantity,actor,note FROM material_purchase_requests WHERE request_key=?",r.requestKey());
        if(!old.isEmpty()) {
            var v=old.get(0);
            if(((Number)v.get("part_no")).longValue()!=id || !WarehouseRules.same((BigDecimal)v.get("quantity"),r.quantity()) || !a.getName().equals(v.get("actor")) || !r.note().equals(v.get("note"))) throw conflict("Ключ заявки вже використано");
        } else db.update("INSERT INTO material_purchase_requests(request_key,part_no,quantity,actor,note) VALUES(?,?,?,?,?)",r.requestKey(),id,r.quantity(),a.getName(),r.note());
        return material(id,a);
    }
    public record RequestState(@PositiveOrZero long expectedRevision,@NotBlank String status) {}
    public Map<String,Object> requestState(long id,long requestId,RequestState r,Authentication a) {
        stockAccess(a);
        var rows=db.queryForList("SELECT status,revision FROM material_purchase_requests WHERE id=? AND part_no=? FOR UPDATE",requestId,id);
        if(rows.isEmpty()) throw conflict("Заявку не знайдено");
        var old=rows.get(0);
        if(((Number)old.get("revision")).longValue()!=r.expectedRevision() || !WarehouseRules.requestTransition(old.get("status").toString(),r.status())) throw conflict("Заявку змінено або перехід недопустимий");
        db.update("UPDATE material_purchase_requests SET status=?,revision=revision+1 WHERE id=?",r.status(),requestId);
        db.update("INSERT INTO material_request_events(request_id,status,actor) VALUES(?,?,?)",requestId,r.status(),a.getName());
        return material(id,a);
    }
    private void uuid(String key){try{UUID.fromString(key);}catch(IllegalArgumentException e){throw conflict("Некоректний ключ операції");}}
    private static PreviewException conflict(String message){return new PreviewException(HttpStatus.CONFLICT,"WAREHOUSE_CONFLICT",message);}
    private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"WAREHOUSE_ACCESS_DENIED","Немає доступу до цієї дії або філії");}
}
