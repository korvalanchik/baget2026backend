package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import jakarta.validation.Valid;
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
public class ProductionService {
    private final JdbcTemplate db;
    private final CalculatedOrderService orders;
    private final InventoryReservations reservations;
    private final UsersRepository users;
    public ProductionService(JdbcTemplate db, CalculatedOrderService orders, InventoryReservations reservations, UsersRepository users) {
        this.db=db; this.orders=orders; this.reservations=reservations; this.users=users;
    }
    public record Line(long itemNo,long productId,String productName,long partNo,String description,String role,String unit,
                       BigDecimal planned,BigDecimal actual,BigDecimal widthMm,BigDecimal heightMm,BigDecimal price,
                       BigDecimal amount,boolean material) {}
    public record View(long orderNo,long revision,int status,boolean finalized,BigDecimal total,Long invoiceNo,List<Line> lines) {}
    public record Actual(@Positive long itemNo,@NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,
                         @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal widthMm,
                         @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal heightMm) {}
    public record Actuals(@Positive long expectedRevision,@NotEmpty @Size(max=5000) List<@Valid Actual> lines) {}
    public record Transition(@Positive long expectedRevision,@Min(1) @Max(12) int status,@Size(max=500) String note) {}
    private void access(Authentication auth,long id) {
        orders.read(id,auth); // Enforces branch access, authentication and new-order identity.
        requireForeman(auth);
    }
    private void requireForeman(Authentication auth) {
        var u=users.findByUsername(auth.getName()).orElseThrow();
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN").contains(r.getName())))
            throw new PreviewException(HttpStatus.FORBIDDEN,"FOREMAN_REQUIRED","Дія доступна майстру або адміністратору");
    }
    private View locked(long id,long revision,Authentication auth) {
        if(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id).isEmpty()) throw conflict("Замовлення не знайдено");
        orders.read(id,auth);
        if(db.queryForObject("SELECT commerce_mode FROM orders WHERE OrderNo=?",String.class,id)!=null)throw conflict("Замовлення з контролем ціни ведеться через картки виробів майстерні");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT workshop_mode FROM orders WHERE OrderNo=?",Boolean.class,id)))
            throw conflict("Замовлення ведеться по виробах. Скористайтеся карткою майстерні");
        var v=view(id,auth);
        if(v.revision()!=revision) throw conflict("Замовлення змінено. Оновіть картку перед повторною дією");
        return v;
    }
    public View view(long id,Authentication auth) {
        accessView(id,auth);
        return db.queryForObject("SELECT calculation_revision,StatusOrder,production_finalized,production_total,(SELECT MAX(inv.invoice_no) FROM invoice_orders io JOIN invoices inv ON inv.id=io.invoice_id WHERE io.order_no=orders.OrderNo AND inv.lifecycle='ACTIVE') FROM orders WHERE OrderNo=?",
            (rs,n)->new View(id,rs.getLong(1),rs.getInt(2),rs.getBoolean(3),rs.getBigDecimal(4),(Long)rs.getObject(5),lines(id)),id);
    }
    private void accessView(long id,Authentication auth) { orders.read(id,auth); }
    private List<Line> lines(long id) {
        return db.query("SELECT i.ItemNo,i.order_product_id,p.client_product_id,i.PartNo,m.Description,i.calculation_role,i.calculation_unit,i.planned_consumption,i.actual_consumption,i.actual_width_mm,i.actual_height_mm,i.billing_unit_price,i.actual_amount,i.stock_material FROM items i JOIN order_products p ON p.id=i.order_product_id JOIN parts m ON m.PartNo=i.PartNo WHERE i.OrderNo=? ORDER BY i.ItemNo",
            (rs,n)->new Line(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getBigDecimal(13),rs.getBoolean(14)),id);
    }
    public View save(long id,Actuals request,Authentication auth) {
        var v=locked(id,request.expectedRevision(),auth);
        requireForeman(auth);
        if(v.finalized() || (v.status()!=2 && v.status()!=3)) throw conflict("Фактичні витрати доступні після прийняття в роботу і до остаточного розрахунку");
        var submitted=new HashMap<Long,Actual>();
        for(var a:request.lines()) if(submitted.put(a.itemNo(),a)!=null) throw conflict("Дубль позиції");
        if(submitted.size()!=v.lines().size()) throw conflict("Заповніть усі позиції");
        for(var l:v.lines()) {
            var a=submitted.get(l.itemNo());
            if(a==null || a.quantity()==null || a.quantity().signum()<0) throw conflict("Некоректна фактична витрата");
            if((a.widthMm()==null)!=(a.heightMm()==null)) throw conflict("Вкажіть обидва розміри або залиште обидва порожніми");
            if("PIECE".equals(l.unit()) && a.quantity().stripTrailingZeros().scale()>0) throw conflict("Штучні позиції потребують цілої кількості");
            var amount=ProductionRules.amount(a.quantity(),l.price());
            db.update("UPDATE items SET actual_consumption=?,actual_width_mm=?,actual_height_mm=?,actual_amount=? WHERE OrderNo=? AND ItemNo=?",
                a.quantity(),a.widthMm(),a.heightMm(),amount,id,l.itemNo());
        }
        bump(id,"ACTUALS",auth,null);
        return view(id,auth);
    }
    public View transition(long id,Transition r,Authentication auth) {
        var v=locked(id,r.expectedRevision(),auth);
        if(r.status()!=11 && r.status()!=12) requireForeman(auth);
        if(!ProductionRules.transition(v.status(),r.status(),v.finalized())) throw conflict("Недопустимий перехід статусу");
        if(r.status()==5 || r.status()==10) {
            if(r.note()==null || r.note().isBlank()) throw conflict("Вкажіть причину припинення виконання");
            if(db.queryForObject("SELECT COUNT(*) FROM invoice_orders WHERE order_no=?",Long.class,id)>0) throw conflict("Спочатку узгодьте виставлений рахунок");
            var parts=reservations.orderParts(id); reservations.lockParts(parts); reservations.release(id,auth.getName());
            for(long p:parts) reservations.refillPart(p,auth.getName());
        }
        db.update("UPDATE orders SET StatusOrder=? WHERE OrderNo=?",r.status(),id);
        if(r.status()==11) db.update("UPDATE orders SET ReceivedDate=CURRENT_TIMESTAMP WHERE OrderNo=?",id);
        if(r.status()==12) db.update("UPDATE orders SET ClientReceivedDate=CURRENT_TIMESTAMP WHERE OrderNo=?",id);
        bump(id,"STATUS_"+r.status(),auth,r.note());
        return view(id,auth);
    }
    public View finalizeProduction(long id,long revision,Authentication auth) {
        var v=locked(id,revision,auth);
        requireForeman(auth);
        if(v.finalized() || v.status()!=3) throw conflict("Остаточний розрахунок доступний для виробленого замовлення один раз");
        if(v.lines().isEmpty() || v.lines().stream().anyMatch(l->l.actual()==null)) throw conflict("Збережіть фактичну витрату для всіх позицій, включно з роботами");
        var expectedTotal=v.lines().stream().map(l->ProductionRules.amount(l.actual(),l.price())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(expectedTotal.signum()<=0) throw conflict("Остаточна вартість повинна бути більшою за нуль");
        var consumption=new TreeMap<Long,BigDecimal>();
        for(var l:v.lines()) if(l.material()) consumption.merge(l.partNo(),l.actual(),BigDecimal::add);
        var parts=new TreeSet<>(reservations.orderParts(id)); parts.addAll(consumption.keySet()); reservations.lockParts(parts);
        for(var entry:consumption.entrySet()) {
            var stock=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,entry.getKey());
            // All reservation writers hold the same Parts lock; reservations of other orders remain protected.
            var other=db.queryForObject("SELECT COALESCE(SUM(r.reserved),0) FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE r.part_no=? AND r.active=1 AND p.order_no<>?",BigDecimal.class,entry.getKey(),id);
            if(!ProductionRules.canConsume(stock,other,entry.getValue())) throw conflict("Недостатньо вільного фізичного залишку або залишок невідомий: "+entry.getKey()+". Оприбуткуйте матеріал і повторіть розрахунок");
        }
        reservations.release(id,auth.getName());
        BigDecimal total=BigDecimal.ZERO;
        for(var l:v.lines()) {
            var amount=ProductionRules.amount(l.actual(),l.price()); total=total.add(amount);
            if(l.material() && l.actual().signum()>0) {
                var before=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,l.partNo());
                var after=before.subtract(l.actual());
                db.update("UPDATE parts SET OnHand=? WHERE PartNo=?",after,l.partNo());
                db.update("INSERT INTO inventory_movements(order_no,item_no,part_no,quantity_delta,stock_before,stock_after,actor) VALUES(?,?,?,?,?,?,?)",
                    id,l.itemNo(),l.partNo(),l.actual().negate(),before,after,auth.getName());
                ConsumptionCost.capture(db,id,l.itemNo());
            }
            db.update("UPDATE items SET Qty=?,Width=?,Height=?,actual_amount=?,`Sum`=? WHERE OrderNo=? AND ItemNo=?",
                l.actual(),l.widthMm()==null?null:l.widthMm().movePointLeft(3),l.heightMm()==null?null:l.heightMm().movePointLeft(3),amount,amount,id,l.itemNo());
        }
        db.update("UPDATE order_products p SET production_state='COMPLETED',production_confirmed=1,current_location='WORKSHOP',actual_total=(SELECT COALESCE(SUM(i.actual_amount),0) FROM items i WHERE i.order_product_id=p.id) WHERE p.order_no=? AND p.active=1",id);
        db.update("UPDATE orders SET production_finalized=1,production_total=?,ItemsTotal=?,TotalCost=(SELECT COALESCE(SUM(COALESCE(i.Cost,0)*i.actual_consumption),0) FROM items i WHERE i.OrderNo=?),StatusOrder=6 WHERE OrderNo=?",total,total,id,id);
        bump(id,"FINALIZE",auth,null);
        for(long p:parts) reservations.refillPart(p,auth.getName());
        return view(id,auth);
    }
    /** Called by the existing invoice service in its transaction; client amounts are ignored. */
    public BigDecimal invoiceAmount(long id,Authentication auth) {
        if("RESELLER".equals(db.queryForObject("SELECT commerce_mode FROM orders WHERE OrderNo=?",String.class,id)))throw conflict("Для реселлерського замовлення потрібен окремий рахунок фірмі за ListPrice_1");
        db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id);
        boolean workshop=Boolean.TRUE.equals(db.queryForObject("SELECT workshop_mode FROM orders WHERE OrderNo=?",Boolean.class,id));
        if(workshop) {
            orders.read(id,auth);
            var u=users.findByUsername(auth.getName()).orElseThrow();
            if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_COUNTER").contains(r.getName())))
                throw new PreviewException(HttpStatus.FORBIDDEN,"ACCOUNTANT_REQUIRED","Рахунок оформлює бухгалтер або адміністратор");
        } else access(auth,id);
        if(workshop && db.queryForObject("SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND (active_rework_id IS NOT NULL OR pending_location='WORKSHOP' OR ((EXISTS(SELECT 1 FROM product_transfers t WHERE t.product_id=order_products.id AND t.to_location='WORKSHOP' AND t.action='ACCEPT') OR EXISTS(SELECT 1 FROM product_reworks rw WHERE rw.product_id=order_products.id)) AND warehouse_received_at IS NULL))",Long.class,id)>0)
            throw conflict("Виріб повертається або доопрацьовується. Після завершення потрібне повторне приймання на склад");
        var v=view(id,auth);
        if(!v.finalized() || !(workshop?Set.of(3,6,11,12):Set.of(6,11,12)).contains(v.status()) || v.total()==null || v.total().signum()<=0)
            throw conflict("Спочатку підтвердьте фактичні витрати й остаточний розрахунок");
        return v.total();
    }
    private void bump(long id,String action,Authentication auth,String note) {
        db.update("UPDATE orders SET calculation_revision=calculation_revision+1 WHERE OrderNo=?",id);
        db.update("INSERT INTO production_events(order_no,revision,action,actor,note) SELECT OrderNo,calculation_revision,?,?,? FROM orders WHERE OrderNo=?",action,auth.getName(),note,id);
    }
    private static PreviewException conflict(String message) {return new PreviewException(HttpStatus.CONFLICT,"PRODUCTION_CONFLICT",message);}
}
