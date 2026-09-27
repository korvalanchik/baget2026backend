package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static com.example.baget.orderproduct.WorkshopRules.State;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class WorkshopService {
    private final JdbcTemplate db;
    private final CalculatedOrderService orders;
    private final ProductionService production;
    private final InventoryReservations reservations;
    private final UsersRepository users;
    private final ObjectMapper json;
    private final ReworkService reworks;
    public WorkshopService(JdbcTemplate db, CalculatedOrderService orders, ProductionService production,
            InventoryReservations reservations, UsersRepository users, ObjectMapper json, ReworkService reworks) {
        this.db=db; this.orders=orders; this.production=production; this.reservations=reservations; this.users=users; this.json=json; this.reworks=reworks;
    }
    public record Event(long revision,String action,String actor,String note,LocalDateTime at,JsonNode snapshot) {}
    public record Product(long id,String name,State state,long revision,BigDecimal widthMm,BigDecimal heightMm,
            String note,String master,LocalDateTime acceptedAt,LocalDateTime completedAt,boolean confirmed,
            BigDecimal total,Long confirmedRevision,JsonNode confirmedCalculation,JsonNode configuration,
            List<ProductionService.Line> lines,List<Event> events,Map<String,Object> rework) {}
    public record View(long orderNo,long revision,int status,boolean canEdit,List<Product> products) {}
    public record Correction(@Positive long expectedRevision,
            @NotNull @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal widthMm,
            @NotNull @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal heightMm,
            @NotBlank @Size(max=2000) String note,
            @NotEmpty @Size(max=5000) List<ProductionService.@NotNull @Valid Actual> lines) {}
    public record Transition(@Positive long expectedRevision,@NotNull State state,@Size(max=2000) String note) {}
    private boolean foreman(Authentication auth) {
        if(auth==null || !auth.isAuthenticated()) return false;
        return users.findByUsername(auth.getName()).map(u->u.getRoles().stream()
                .anyMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN").contains(r.getName()))).orElse(false);
    }
    public View view(long id,Authentication auth) {
        var order=orders.read(id,auth); // Database roles and branch scope are authoritative.
        var lines=production.view(id,auth).lines();
        var products=db.query("SELECT * FROM order_products WHERE order_no=? AND active=1 ORDER BY id",(rs,n)-> {
            long pid=rs.getLong("id");
            return new Product(pid,rs.getString("client_product_id"),State.valueOf(rs.getString("production_state")),
                rs.getLong("production_revision"),rs.getBigDecimal("measured_width_mm"),rs.getBigDecimal("measured_height_mm"),
                rs.getString("workshop_note"),rs.getString("assigned_master"),rs.getObject("accepted_at",LocalDateTime.class),
                rs.getObject("completed_at",LocalDateTime.class),rs.getBoolean("production_confirmed"),rs.getBigDecimal("actual_total"),
                rs.getObject("confirmed_revision",Long.class),decode(rs.getString("confirmed_calculation")),decode(rs.getString("configuration")),
                lines.stream().filter(l->l.productId()==pid).toList(),events(pid),reworks.view(id,pid));
        },id);
        return new View(id,order.revision(),order.statusOrder(),foreman(auth),products);
    }
    private List<Event> events(long pid) {
        return db.query("SELECT revision,action,actor,note,created_at,snapshot FROM product_production_events WHERE order_product_id=? ORDER BY revision DESC",
            (rs,n)->new Event(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5,LocalDateTime.class),decode(rs.getString(6))),pid);
    }
    private Product locked(long id,long pid,long revision,Authentication auth) {
        if(!foreman(auth)) throw new PreviewException(HttpStatus.FORBIDDEN,"FOREMAN_REQUIRED","Дія доступна майстру або адміністратору");
        if(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id).isEmpty()) throw conflict("Замовлення не знайдено");
        var v=view(id,auth);
        if(v.revision()!=revision) throw conflict("Дані змінилися. Оновіть картку перед повторною дією");
        if(Set.of(5,10).contains(v.status())) throw conflict("Виконання замовлення припинено");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT production_finalized FROM orders WHERE OrderNo=?",Boolean.class,id)))
            throw conflict("Витрати замовлення вже підтверджені");
        if(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,id)>0)
            throw conflict("Для замовлення вже оформлено рахунок. Потрібне окреме узгодження");
        var product=v.products().stream().filter(p->p.id()==pid).findFirst().orElseThrow(()->conflict("Виріб не належить цьому замовленню або вже неактивний"));
        if(product.rework()!=null && product.rework().get("active")!=null) throw conflict("Відкрито доопрацювання. Використайте окрему форму додаткових витрат");
        if(product.confirmed()) throw conflict("Виріб завершено. Підтверджений розрахунок захищено від змін");
        // Take ownership from the order-level API within the same transaction.
        db.update("UPDATE orders SET workshop_mode=1 WHERE OrderNo=?",id);
        return product;
    }
    public View transition(long id,long pid,Transition r,Authentication auth) {
        var p=locked(id,pid,r.expectedRevision(),auth);
        if(!WorkshopRules.transition(p.state(),r.state())) throw conflict("Недопустимий перехід стану виробу");
        if(WorkshopRules.needsReason(p.state(),r.state()) && (r.note()==null || r.note().isBlank())) throw conflict("Вкажіть причину призупинення або відновлення");
        db.update("UPDATE order_products SET production_state=? WHERE id=?",r.state().name(),pid);
        if(r.state()==State.ACCEPTED) db.update("UPDATE order_products SET current_location='WORKSHOP',assigned_master=?,accepted_at=CURRENT_TIMESTAMP(6) WHERE id=?",auth.getName(),pid);
        // Imported in-progress orders retain their previous state; first new action records the responsible master.
        db.update("UPDATE order_products SET assigned_master=COALESCE(assigned_master,?),accepted_at=COALESCE(accepted_at,CURRENT_TIMESTAMP(6)) WHERE id=?",auth.getName(),pid);
        audit(id,pid,"STATE_"+r.state(),r.note(),auth);
        aggregate(id);
        return view(id,auth);
    }
    public View save(long id,long pid,Correction r,Authentication auth) {
        var p=locked(id,pid,r.expectedRevision(),auth);
        if(!WorkshopRules.editable(p.state())) throw conflict("Обмір і витрати доступні після приймання, до завершення, без призупинення");
        var submitted=new HashMap<Long,ProductionService.Actual>();
        for(var a:r.lines()) if(submitted.put(a.itemNo(),a)!=null) throw conflict("Дубль позиції");
        if(submitted.size()!=p.lines().size()) throw conflict("Заповніть усі позиції цього виробу");
        for(var line:p.lines()) {
            var a=submitted.get(line.itemNo());
            if(a==null || a.quantity()==null || a.quantity().signum()<0) throw conflict("Некоректна фактична витрата");
            if((a.widthMm()==null)!=(a.heightMm()==null)) throw conflict("Вкажіть обидва розміри позиції або залиште обидва порожніми");
            if("PIECE".equals(line.unit()) && a.quantity().stripTrailingZeros().scale()>0) throw conflict("Штучні позиції потребують цілої кількості");
            if(line.price()==null || line.price().signum()<0) throw conflict("Не задана коректна ціна позиції");
            db.update("UPDATE items SET actual_consumption=?,actual_width_mm=?,actual_height_mm=?,actual_amount=? WHERE OrderNo=? AND ItemNo=? AND order_product_id=?",
                a.quantity(),a.widthMm(),a.heightMm(),ProductionRules.amount(a.quantity(),line.price()),id,line.itemNo(),pid);
        }
        // Actual measurements replace the product's remaining demand, not physical stock.
        var demand=new TreeMap<Long,BigDecimal>();
        for(var line:p.lines())if(line.material())demand.merge(line.partNo(),submitted.get(line.itemNo()).quantity(),BigDecimal::add);
        var partIds=new TreeSet<>(db.queryForList("SELECT part_no FROM material_reservations WHERE order_product_id=? AND active=1",Long.class,pid));
        partIds.addAll(demand.keySet());reservations.lockParts(partIds);
        for(long part:partIds)reservations.productionDemand(pid,part,demand.getOrDefault(part,BigDecimal.ZERO),auth.getName());
        for(long part:partIds)reservations.refillPart(part,auth.getName());
        db.update("UPDATE order_products SET measured_width_mm=?,measured_height_mm=?,workshop_note=?,assigned_master=COALESCE(assigned_master,?),accepted_at=COALESCE(accepted_at,CURRENT_TIMESTAMP(6)) WHERE id=?",
            r.widthMm(),r.heightMm(),r.note(),auth.getName(),pid);
        audit(id,pid,"CORRECTION",r.note(),auth);
        aggregate(id);
        return view(id,auth);
    }
    public View complete(long id,long pid,long revision,Authentication auth) {
        var p=locked(id,pid,revision,auth);
        if(p.state()!=State.IN_PROGRESS) throw conflict("Завершити можна лише виріб у роботі");
        if(p.widthMm()==null || p.heightMm()==null || p.lines().isEmpty() || p.lines().stream().anyMatch(l->l.actual()==null))
            throw conflict("Збережіть обмір і фактичні витрати всіх позицій виробу");
        var total=p.lines().stream().map(l->ProductionRules.amount(l.actual(),l.price())).reduce(BigDecimal.ZERO,BigDecimal::add);
        var consumption=new TreeMap<Long,BigDecimal>();
        for(var l:p.lines()) if(l.material()) consumption.merge(l.partNo(),l.actual(),BigDecimal::add);
        var partIds=new TreeSet<>(db.queryForList("SELECT part_no FROM material_reservations WHERE order_product_id=? AND active=1",Long.class,pid));
        partIds.addAll(consumption.keySet()); reservations.lockParts(partIds);
        for(var e:consumption.entrySet()) {
            var stock=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,e.getKey());
            // Includes reservations of sibling products in this very order.
            var other=db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,e.getKey(),pid);
            if(!ProductionRules.canConsume(stock,other,e.getValue())) throw conflict("Недостатньо вільного залишку матеріалу "+e.getKey()+". Поповніть склад або узгодьте резерви; виріб залишається у роботі");
        }
        reservations.releaseProduct(pid,auth.getName());
        for(var l:p.lines()) {
            var amount=ProductionRules.amount(l.actual(),l.price());
            if(l.material() && l.actual().signum()>0) {
                var before=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,l.partNo());
                var after=before.subtract(l.actual());
                db.update("UPDATE parts SET OnHand=? WHERE PartNo=?",after,l.partNo());
                db.update("INSERT INTO inventory_movements(order_no,item_no,part_no,quantity_delta,stock_before,stock_after,actor) VALUES(?,?,?,?,?,?,?)",
                    id,l.itemNo(),l.partNo(),l.actual().negate(),before,after,auth.getName());
            }
            db.update("UPDATE items SET Qty=?,Width=?,Height=?,actual_amount=?,`Sum`=? WHERE OrderNo=? AND ItemNo=? AND order_product_id=?",
                l.actual(),l.widthMm()==null?null:l.widthMm().movePointLeft(3),l.heightMm()==null?null:l.heightMm().movePointLeft(3),amount,amount,id,l.itemNo(),pid);
        }
        // Snapshot is immutable once confirmed and belongs to one entire OrderProduct, including its quantity.
        db.update("UPDATE order_products SET production_state='COMPLETED',production_confirmed=1,completed_at=CURRENT_TIMESTAMP(6),actual_total=?,confirmed_revision=production_revision+1,confirmed_calculation=? WHERE id=?",
            total,encode(new Confirmation(1,pid,p.revision()+1,p.widthMm(),p.heightMm(),p.configuration(),p.lines(),total,auth.getName())),pid);
        audit(id,pid,"COMPLETE","Підтверджено фактичні витрати та завершено виріб",auth);
        aggregate(id);
        for(long part:partIds) reservations.refillPart(part,auth.getName());
        return view(id,auth);
    }
    public record Confirmation(int schemaVersion,long productId,long revision,BigDecimal widthMm,BigDecimal heightMm,
            JsonNode configuration,List<ProductionService.Line> lines,BigDecimal total,String actor) {}
    private void aggregate(long id) {
        long remaining=db.queryForObject("SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=0",Long.class,id);
        var total=db.queryForObject("SELECT COALESCE(SUM(actual_total),0) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=1",BigDecimal.class,id);
        // Compatibility summary only; product states stay independent of invoice/payment statuses.
        db.update("UPDATE orders SET production_finalized=?,production_total=?,StatusOrder=CASE WHEN StatusOrder IN (1,2,3) THEN ? ELSE StatusOrder END WHERE OrderNo=?",
            remaining==0,total,remaining==0?3:2,id);
        if(remaining==0) db.update("UPDATE orders SET ItemsTotal=?,TotalCost=(SELECT COALESCE(SUM(COALESCE(Cost,0)*actual_consumption),0) FROM items WHERE OrderNo=?) WHERE OrderNo=?",total,id,id);
    }
    private void audit(long id,long pid,String action,String note,Authentication auth) {
        db.update("UPDATE order_products SET production_revision=production_revision+1 WHERE id=?",pid);
        db.update("UPDATE orders SET calculation_revision=calculation_revision+1 WHERE OrderNo=?",id);
        var p=view(id,auth).products().stream().filter(v->v.id()==pid).findFirst().orElseThrow();
        var snapshot=new Confirmation(1,pid,p.revision(),p.widthMm(),p.heightMm(),p.configuration(),p.lines(),p.total(),auth.getName());
        db.update("INSERT INTO product_production_events(order_product_id,revision,action,actor,note,snapshot) VALUES(?,?,?,?,?,?)",
            pid,p.revision(),action,auth.getName(),note,encode(snapshot));
    }
    private JsonNode decode(String value) { try { return value==null?null:json.readTree(value); } catch(Exception e) { throw new IllegalStateException("Saved workshop JSON",e); } }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Workshop JSON",e); } }
    private static PreviewException conflict(String text) { return new PreviewException(HttpStatus.CONFLICT,"WORKSHOP_CONFLICT",text); }
}
