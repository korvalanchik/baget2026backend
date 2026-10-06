package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PartPriceResolver;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.parts.PartsRepository;
import com.example.baget.parts.PartKind;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.baget.orderproduct.ReworkRules.ChargeMode;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ReworkService {
    private final JdbcTemplate db;
    private final CalculatedOrderService orders;
    private final ProductionService production;
    private final InventoryReservations reservations;
    private final UsersRepository users;
    private final PartsRepository parts;
    private final PartPriceResolver prices;
    private final ObjectMapper json;
    private final Validator validator;
    public ReworkService(JdbcTemplate db, CalculatedOrderService orders, ProductionService production,
                         InventoryReservations reservations, UsersRepository users, PartsRepository parts,
                         PartPriceResolver prices, ObjectMapper json, Validator validator) {
        this.db=db; this.orders=orders; this.production=production; this.reservations=reservations;
        this.users=users; this.parts=parts; this.prices=prices; this.json=json; this.validator=validator;
    }
    public record Open(@Positive long expectedRevision, @NotNull ChargeMode chargeMode,
                       @NotBlank @Size(max=2000) String reason) {}
    public record Extra(@Positive Long sourceItemNo, @Positive long partNo,
                        @NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal quantity,
                        @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal widthMm,
                        @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal heightMm,
                        @Pattern(regexp="ARTWORK|OUTER") String basis,@Min(1) @Max(10000) Integer unitsPerProduct) {
        public Extra(Long sourceItemNo,long partNo,BigDecimal quantity,BigDecimal widthMm,BigDecimal heightMm) {
            this(sourceItemNo,partNo,quantity,widthMm,heightMm,null,null);
        }
    }
    public record Save(@Positive long expectedRevision,
                       @NotNull @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal widthMm,
                       @NotNull @Positive @DecimalMax("999999") @Digits(integer=6,fraction=3) BigDecimal heightMm,
                       @NotBlank @Size(max=2000) String note,
                       @NotNull @Size(max=200) List<@NotNull @Valid Extra> lines) {}

    private void validate(Object r) {
        if(r==null || !validator.validate(r).isEmpty()) throw conflict("Перевірте причину, розміри та додаткові витрати");
    }
    private Authentication foreman(Authentication a) {
        if(a==null || !a.isAuthenticated()) throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var u=users.findByUsername(a.getName()).orElseThrow(()->new PreviewException(HttpStatus.FORBIDDEN,"FOREMAN_REQUIRED","Користувача не знайдено"));
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_FOREMAN").contains(r.getName())))
            throw new PreviewException(HttpStatus.FORBIDDEN,"FOREMAN_REQUIRED","Доопрацювання оформлює майстер або адміністратор");
        return new UsernamePasswordAuthenticationToken(a.getName(),"",u.getRoles().stream().map(r->new SimpleGrantedAuthority(r.getName())).toList());
    }
    private Map<String,Object> lock(long orderNo,long pid,long revision,Authentication a) {
        foreman(a);
        if(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,orderNo).isEmpty()) throw conflict("Замовлення не знайдено");
        var order=orders.read(orderNo,a);
        if(order.revision()!=revision) throw conflict("Дані змінилися. Оновіть картку перед повторною дією");
        if(Set.of(5,10,12).contains(order.statusOrder())) throw conflict("Замовлення припинене або видане клієнту; потрібен окремий процес повернення");
        var rows=db.queryForList("SELECT * FROM order_products WHERE id=? AND order_no=? AND active=1 FOR UPDATE",pid,orderNo);
        if(rows.isEmpty()) throw conflict("Виріб не належить цьому замовленню");
        if(invoiced(orderNo,pid)) throw conflict("За виробом уже оформлено рахунок. Доопрацювання після рахунку потребує фінансового коригування");
        return rows.get(0);
    }
    public boolean invoiced(long orderNo,long pid) {
        return db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE product_id=?",Long.class,pid)>0
            || db.queryForObject("SELECT COUNT(*) FROM product_invoice_claims WHERE product_id=?",Long.class,pid)>0
            || db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,orderNo)>0;
    }
    private boolean yes(Object v) { return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()==1; }
    private long number(Object v) { return ((Number)v).longValue(); }
    private JsonNode decode(Object value) {
        try {return value==null?null:json.readTree(value.toString());} catch(Exception e) {throw new IllegalStateException("Rework JSON",e);}
    }
    private String encode(Object value) {
        try {return json.writeValueAsString(value);} catch(Exception e) {throw new IllegalStateException("Rework JSON",e);}
    }
    /** Internal read; caller has already checked the order's branch access. */
    public Map<String,Object> view(long orderNo,long pid) {
        var rows=db.queryForList("SELECT id,opened_revision AS openedRevision,charge_mode AS chargeMode,reason,actor,state,baseline_total AS baselineTotal,width_mm AS widthMm,height_mm AS heightMm,work_note AS note,saved,created_at AS createdAt,completed_at AS completedAt,source_confirmation AS sourceConfirmation,final_confirmation AS finalConfirmation FROM product_reworks WHERE product_id=? ORDER BY id DESC",pid);
        var position=db.queryForMap("SELECT current_location AS location,pending_location AS pending FROM order_products WHERE id=? AND order_no=?",pid,orderNo);
        Map<String,Object> active=null;
        for(var row:rows) {
            row.put("saved",yes(row.get("saved")));
            row.put("sourceConfirmation",decode(row.get("sourceConfirmation")));
            row.put("finalConfirmation",decode(row.get("finalConfirmation")));
            var detail=lines(number(row.get("id")));BigDecimal additional=BigDecimal.ZERO;
            var mode=ChargeMode.valueOf(row.get("chargeMode").toString());
            for(var line:detail) {
                var amount=ReworkRules.charge(mode,(BigDecimal)line.get("quantity"),(BigDecimal)line.get("price"));
                line.put("amount",amount);additional=additional.add(amount);
                line.remove("cost"); // Cost remains stored internally; workshop detail exposes sale amounts only.
            }
            row.put("lines",detail);row.put("additionalCharge",additional);
            row.put("projectedTotal",((BigDecimal)row.get("baselineTotal")).add(additional));
            if("OPEN".equals(row.get("state"))) active=row;
        }
        var result=new LinkedHashMap<String,Object>();result.put("active",active);result.put("history",rows);
        result.putAll(position);
        result.put("invoiced",invoiced(orderNo,pid));result.put("reservations",reservations.balances(pid));
        return result;
    }
    private List<Map<String,Object>> lines(long reworkId) {
        return db.queryForList("SELECT line_no AS lineNo,source_item_no AS sourceItemNo,part_no AS partNo,description,unit,material,quantity,unit_price AS price,unit_cost AS cost,width_mm AS widthMm,height_mm AS heightMm,measurement_basis AS basis,calculation_units AS unitsPerProduct FROM product_rework_lines WHERE rework_id=? ORDER BY line_no",reworkId);
    }
    public List<Map<String,Object>> catalog(long orderNo,String q,Authentication a) {
        foreman(a);orders.read(orderNo,a);
        if(q==null || q.trim().length()<2) return List.of();
        String term=q.trim();
        return db.queryForList("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,part_kind AS kind FROM parts WHERE part_kind IN ('MATERIAL','WORK') AND unit_type IN ('METER','SQUARE_METER','PIECE') AND calculation_method IS NOT NULL AND calculation_params IS NOT NULL AND (LOCATE(?,Description)>0 OR LOCATE(?,CAST(PartNo AS CHAR))>0) ORDER BY PartNo LIMIT 30",term,term);
    }
    public void open(long orderNo,long pid,Open r,Authentication a) {
        validate(r);foreman(a);
        if(r.chargeMode()==ChargeMode.CUSTOMER) throw conflict("Зміни клієнта після початку роботи оформлюються новим замовленням");
        var p=lock(orderNo,pid,r.expectedRevision(),a);
        if(!ReworkRules.canOpen(yes(p.get("production_confirmed")),(String)p.get("production_state"),
                (String)p.get("current_location"),(String)p.get("pending_location"),false,p.get("active_rework_id")!=null))
            throw conflict("Поверніть завершений виріб до майстерні та підтвердьте приймання. Передача має бути завершена");
        var source=decode(p.get("confirmed_calculation"));var baseline=(BigDecimal)p.get("actual_total");
        if(source==null || baseline==null || p.get("confirmed_revision")==null
                || source.path("productId").asLong()!=pid || source.path("revision").asLong()!=number(p.get("confirmed_revision"))
                || source.path("total").decimalValue().compareTo(baseline)!=0)
            throw conflict("Відсутній узгоджений підтверджений розрахунок; старі вироби автоматично не переносяться");
        long next=number(p.get("production_revision"))+1;
        db.update("INSERT INTO product_reworks(product_id,opened_revision,charge_mode,reason,actor,source_confirmation,baseline_total,width_mm,height_mm) VALUES(?,?,?,?,?,?,?,?,?)",
                pid,next,r.chargeMode().name(),r.reason().trim(),a.getName(),encode(source),baseline,p.get("measured_width_mm"),p.get("measured_height_mm"));
        long rid=db.queryForObject("SELECT id FROM product_reworks WHERE product_id=? AND opened_revision=?",Long.class,pid,next);
        db.update("UPDATE order_products SET active_rework_id=?,production_state='IN_PROGRESS',production_confirmed=0,confirmed_revision=NULL,confirmed_calculation=NULL,completed_at=NULL,warehouse_received_at=NULL,tracking_revision=tracking_revision+1,assigned_master=?,workshop_note=? WHERE id=?",rid,a.getName(),r.reason().trim(),pid);
        db.update("UPDATE orders SET workshop_mode=1 WHERE OrderNo=?",orderNo);
        event(orderNo,pid,"REWORK_OPEN",r.reason().trim(),source,a);
        aggregate(orderNo);
    }
    private Map<String,Object> active(Map<String,Object> p) {
        if(p.get("active_rework_id")==null || yes(p.get("production_confirmed")) || !"IN_PROGRESS".equals(p.get("production_state"))
                || !"WORKSHOP".equals(p.get("current_location")) || p.get("pending_location")!=null)
            throw conflict("Немає активного доопрацювання у майстерні");
        var row=db.queryForMap("SELECT * FROM product_reworks WHERE id=? AND product_id=? AND state='OPEN' FOR UPDATE",p.get("active_rework_id"),p.get("id"));
        return row;
    }
    private String key(Long source,long part) {return source==null?"part:"+part:"item:"+source;}
    private Map<String,Object> metadata(long orderNo,long pid,Extra extra,Authentication pricingAuth) {
        if(extra.sourceItemNo()!=null) {
            var rows=db.queryForList("SELECT i.PartNo AS partNo,p.Description AS description,i.calculation_unit AS unit,i.stock_material AS material,i.billing_unit_price AS price,i.Cost AS cost FROM items i JOIN parts p ON p.PartNo=i.PartNo WHERE i.OrderNo=? AND i.ItemNo=? AND i.order_product_id=?",orderNo,extra.sourceItemNo(),pid);
            if(rows.isEmpty() || number(rows.get(0).get("partNo"))!=extra.partNo()) throw conflict("Позиція не належить цьому виробу");
            var row=rows.get(0);var value=row.get("cost");
            if(value!=null)row.put("cost",new BigDecimal(value.toString()));
            return row;
        }
        var part=parts.findById(extra.partNo()).orElseThrow(()->conflict("Матеріал не знайдено"));
        if(part.getUnitType()==null || part.getPartKind()==null || part.getCalculationMethod()==null || part.getCalculationParams()==null)
            throw conflict("Новий матеріал ще не налаштований для розрахунку");
        var row=new LinkedHashMap<String,Object>();row.put("description",part.getDescription());row.put("unit",part.getUnitType().name());
        row.put("material",part.getPartKind()==PartKind.MATERIAL);row.put("price",prices.resolve(part,pricingAuth));
        if(part.getCost()!=null && (!Double.isFinite(part.getCost()) || part.getCost()<0)) throw conflict("Некоректна собівартість матеріалу");
        row.put("cost",part.getCost()==null?null:BigDecimal.valueOf(part.getCost()));return row;
    }
    public void save(long orderNo,long pid,Save r,Authentication a) {
        validate(r);var p=lock(orderNo,pid,r.expectedRevision(),a);var cycle=active(p);long rid=number(cycle.get("id"));
        var prior=new HashMap<String,Map<String,Object>>();
        for(var line:lines(rid)) prior.put(key(line.get("sourceItemNo")==null?null:number(line.get("sourceItemNo")),number(line.get("partNo"))),line);
        var seen=new HashSet<String>();var prepared=new ArrayList<Map<String,Object>>();var demand=new TreeMap<Long,BigDecimal>();
        Authentication pricingAuth=foreman(a);
        for(var extra:r.lines()) {
            String key=key(extra.sourceItemNo(),extra.partNo());if(!seen.add(key))throw conflict("Дубль додаткової позиції");
            if((extra.widthMm()==null)!=(extra.heightMm()==null))throw conflict("Вкажіть обидва розміри позиції або залиште обидва порожніми");
            var meta=prior.get(key);
            if(meta==null || number(meta.get("partNo"))!=extra.partNo())meta=metadata(orderNo,pid,extra,pricingAuth);
            var line=new LinkedHashMap<>(meta);
            var price=(BigDecimal)line.get("price");
            if(price==null || price.signum()<0 || !Set.of("METER","SQUARE_METER","PIECE").contains(line.get("unit")))throw conflict("Некоректна ціна або одиниця позиції");
            try {ReworkRules.quantity(line.get("unit").toString(),extra.quantity());}catch(IllegalArgumentException e){throw conflict(e.getMessage());}
            line.put("partNo",extra.partNo());line.put("sourceItemNo",extra.sourceItemNo());line.put("quantity",extra.quantity());
            line.put("widthMm",extra.widthMm());line.put("heightMm",extra.heightMm());
            line.put("basis",extra.basis()==null?"OUTER":extra.basis());line.put("unitsPerProduct",extra.unitsPerProduct()==null?1:extra.unitsPerProduct());prepared.add(line);
            if(yes(line.get("material")))demand.merge(extra.partNo(),extra.quantity(),BigDecimal::add);
        }
        var partIds=new TreeSet<>(db.queryForList("SELECT part_no FROM material_reservations WHERE order_product_id=? AND active=1",Long.class,pid));partIds.addAll(demand.keySet());reservations.lockParts(partIds);
        db.update("DELETE FROM product_rework_lines WHERE rework_id=?",rid);int index=0;
        for(var line:prepared)db.update("INSERT INTO product_rework_lines(rework_id,line_no,source_item_no,part_no,description,unit,material,quantity,unit_price,unit_cost,width_mm,height_mm,measurement_basis,calculation_units) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rid,++index,line.get("sourceItemNo"),line.get("partNo"),line.get("description"),line.get("unit"),yes(line.get("material")),line.get("quantity"),line.get("price"),line.get("cost"),line.get("widthMm"),line.get("heightMm"),line.get("basis"),line.get("unitsPerProduct"));
        for(long part:partIds)reservations.reworkDemand(pid,part,demand.getOrDefault(part,BigDecimal.ZERO),a.getName());
        for(long part:partIds)reservations.refillPart(part,a.getName());
        db.update("UPDATE product_reworks SET width_mm=?,height_mm=?,work_note=?,saved=1 WHERE id=?",r.widthMm(),r.heightMm(),r.note().trim(),rid);
        db.update("UPDATE order_products SET measured_width_mm=?,measured_height_mm=?,workshop_note=? WHERE id=?",r.widthMm(),r.heightMm(),r.note().trim(),pid);
        event(orderNo,pid,"REWORK_SAVE",r.note().trim(),Map.of("widthMm",r.widthMm(),"heightMm",r.heightMm(),"additionalLines",prepared,"chargeMode",cycle.get("charge_mode")),a);
    }
    public void complete(long orderNo,long pid,long expectedRevision,Authentication a) {
        var p=lock(orderNo,pid,expectedRevision,a);var cycle=active(p);long rid=number(cycle.get("id"));
        if(!yes(cycle.get("saved")))throw conflict("Спочатку збережіть новий обмір і додаткові витрати (список може бути порожнім)");
        var extras=lines(rid);var demand=new TreeMap<Long,BigDecimal>();
        for(var line:extras)if(yes(line.get("material")))demand.merge(number(line.get("partNo")),(BigDecimal)line.get("quantity"),BigDecimal::add);
        var partIds=new TreeSet<>(db.queryForList("SELECT part_no FROM material_reservations WHERE order_product_id=? AND active=1",Long.class,pid));partIds.addAll(demand.keySet());reservations.lockParts(partIds);
        for(var e:demand.entrySet()) {
            var stock=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,e.getKey());
            var other=db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,e.getKey(),pid);
            if(!ProductionRules.canConsume(stock,other,e.getValue()))throw conflict("Недостатньо вільного матеріалу "+e.getKey()+". Доопрацювання залишається відкритим до поповнення складу");
        }
        reservations.releaseProduct(pid,a.getName());
        long nextItem=db.queryForObject("SELECT COALESCE(MAX(ItemNo),0)+1 FROM items WHERE OrderNo=?",Long.class,orderNo);
        BigDecimal added=BigDecimal.ZERO;var mode=ChargeMode.valueOf(cycle.get("charge_mode").toString());
        for(var line:extras) {
            long item=nextItem++,part=number(line.get("partNo"));var qty=(BigDecimal)line.get("quantity");
            BigDecimal amount=ReworkRules.charge(mode,qty,(BigDecimal)line.get("price"));added=added.add(amount);
            var width=(BigDecimal)line.get("widthMm");var height=(BigDecimal)line.get("heightMm");boolean material=yes(line.get("material"));
            // Each cycle appends new items. The UNIQUE inventory movement key continues to prevent double posting.
            db.update("INSERT INTO items(OrderNo,ItemNo,PartNo,Qty,Quantity,SellPrice,Discount,Cost,Width,Height,`Sum`,order_product_id,planned_consumption,calculated_amount,calculation_role,calculation_unit,stock_material,billing_unit_price,actual_consumption,actual_width_mm,actual_height_mm,actual_amount,rework_id) VALUES(?,?,?,?,1,?,0,?,?,?,?,?,0,0,?,?,?,?,?,?,?,?,?)",
                    orderNo,item,part,qty,line.get("price"),line.get("cost"),width==null?null:width.movePointLeft(3),height==null?null:height.movePointLeft(3),amount,pid,
                    mode==ChargeMode.WORKSHOP?"REWORK_INTERNAL":"REWORK_CUSTOMER",line.get("unit"),material,line.get("price"),qty,width,height,amount,rid);
            if(material) {
                var before=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,part);var after=before.subtract(qty);
                db.update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",after,part);
                db.update("INSERT INTO inventory_movements(order_no,item_no,part_no,quantity_delta,stock_before,stock_after,actor) VALUES(?,?,?,?,?,?,?)",orderNo,item,part,qty.negate(),before,after,a.getName());
                ConsumptionCost.capture(db,orderNo,item);
            }
        }
        BigDecimal total=((BigDecimal)cycle.get("baseline_total")).add(added);long nextRevision=number(p.get("production_revision"))+1;
        var confirmation=new LinkedHashMap<String,Object>();confirmation.put("schemaVersion",2);confirmation.put("productId",pid);confirmation.put("revision",nextRevision);
        confirmation.put("widthMm",cycle.get("width_mm"));confirmation.put("heightMm",cycle.get("height_mm"));confirmation.put("configuration",decode(p.get("configuration")));
        confirmation.put("lines",production.view(orderNo,a).lines().stream().filter(l->l.productId()==pid).toList());confirmation.put("total",total);confirmation.put("actor",a.getName());
        confirmation.put("reworkId",rid);confirmation.put("chargeMode",mode);confirmation.put("additionalCharge",added);confirmation.put("reason",cycle.get("reason"));
        db.update("UPDATE product_reworks SET state='COMPLETED',completed_at=CURRENT_TIMESTAMP(6),completed_revision=?,final_confirmation=? WHERE id=?",nextRevision,encode(confirmation),rid);
        db.update("UPDATE order_products SET active_rework_id=NULL,production_state='COMPLETED',production_confirmed=1,completed_at=CURRENT_TIMESTAMP(6),actual_total=?,confirmed_revision=?,confirmed_calculation=?,warehouse_received_at=NULL,tracking_revision=tracking_revision+1 WHERE id=?",total,nextRevision,encode(confirmation),pid);
        event(orderNo,pid,"REWORK_COMPLETE",cycle.get("work_note").toString(),confirmation,a);aggregate(orderNo);
        for(long part:partIds)reservations.refillPart(part,a.getName());
    }
    private void event(long orderNo,long pid,String action,String note,Object snapshot,Authentication a) {
        db.update("UPDATE order_products SET production_revision=production_revision+1 WHERE id=?",pid);
        db.update("UPDATE orders SET calculation_revision=calculation_revision+1 WHERE OrderNo=?",orderNo);
        db.update("INSERT INTO product_production_events(order_product_id,revision,action,actor,note,snapshot) SELECT id,production_revision,?,?,?,? FROM order_products WHERE id=?",action,a.getName(),note,encode(snapshot),pid);
    }
    private void aggregate(long orderNo) {
        long remaining=db.queryForObject("SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=0",Long.class,orderNo);
        var total=db.queryForObject("SELECT COALESCE(SUM(actual_total),0) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=1",BigDecimal.class,orderNo);
        db.update("UPDATE orders SET production_finalized=?,production_total=?,StatusOrder=CASE WHEN StatusOrder IN (1,2,3,6,11) THEN ? ELSE StatusOrder END WHERE OrderNo=?",remaining==0,total,remaining==0?3:2,orderNo);
        if(remaining==0)db.update("UPDATE orders SET ItemsTotal=?,TotalCost=(SELECT COALESCE(SUM(COALESCE(Cost,0)*actual_consumption),0) FROM items WHERE OrderNo=?) WHERE OrderNo=?",total,orderNo,orderNo);
    }
    private static PreviewException conflict(String message) {return new PreviewException(HttpStatus.CONFLICT,"REWORK_CONFLICT",message);}
}
