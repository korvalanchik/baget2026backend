package com.example.baget.orderproduct;

import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.product.ProductPreviewResponse;
import com.example.baget.customer.*;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CalculatedOrderService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher orderEvents;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.baget.commercial.CommercialService commercial;
    private final JdbcTemplate db;
    private final EstimatePreviewService preview;
    private final InventoryReservations reservations;
    private final UsersRepository users;
    private final CustomerService customers;
    private final ObjectMapper json;
    private final Validator validator;
    private final EntityManager em;
    public CalculatedOrderService(JdbcTemplate db, EstimatePreviewService preview, InventoryReservations reservations,
            UsersRepository users, CustomerService customers, ObjectMapper json, Validator validator, EntityManager em) {
        this.db=db;this.preview=preview;this.reservations=reservations;this.users=users;this.customers=customers;this.json=json;this.validator=validator;this.em=em;
    }
    public record ProductView(long id, String clientProductId, JsonNode configuration, JsonNode calculation,
                              BigDecimal total, List<InventoryReservations.Balance> materials) {}
    public record View(long orderNo,long revision,int statusOrder,String branchName,long custNo,String company,
                       String phone,String address,String notice,LocalDate dueDate,BigDecimal total,List<ProductView> products) {}
    private User access(Authentication auth,String branch) {
        if(auth==null || !auth.isAuthenticated()) throw error(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var user=users.findByUsername(auth.getName()).orElseThrow(()->error(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Користувача не знайдено"));
        var roles=user.getRoles().stream().map(Role::getName).toList();
        if(roles.stream().noneMatch(Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_LEVEL2","ROLE_MANAGER","ROLE_SELLER","ROLE_FOREMAN")::contains))
            throw error(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Немає права оформлення замовлення");
        if(!roles.contains("ROLE_ADMIN") && user.getAllowedBranches().stream().noneMatch(b->b.getName().equals(branch)))
            throw error(HttpStatus.FORBIDDEN,"BRANCH_ACCESS_DENIED","Немає доступу до цієї філії");
        return user;
    }
    private void validate(CalculatedOrderRequest r) {
        if(r==null || !validator.validate(r).isEmpty()) throw bad("Перевірте клієнта, філію та склад виробів");
        try { UUID.fromString(r.requestKey()); } catch(IllegalArgumentException e) { throw bad("Некоректний ключ запиту"); }
        if(r.custNo()==null && (r.company()==null || r.company().isBlank() || r.phone()==null || r.phone().isBlank()))
            throw bad("Оберіть клієнта або введіть його ім’я та телефон");
        var ids=new HashSet<String>();int count=0;
        for(var p:r.products()) {
            if(!ids.add(p.clientProductId()) || !p.form().isObject() || encode(p.form()).length()>32000) throw bad("Некоректна конфігурація виробу");
            ProductAttachmentValidation.validate(p.components());
            count+=p.components().size();
        }
        if(count>50) throw bad("Кошторис може містити до 50 складових");
    }
    private record Priced(CalculatedOrderRequest.Product draft,EstimatePreviewResponse result) {}
    private List<Priced> price(CalculatedOrderRequest r,Authentication auth,String mode) {
        auth=commercial.pricing(auth,mode);
        var list=new ArrayList<Priced>();
        for(var product:r.products()) {
            // Component IDs are assigned by the server within each physical product.
            var components=new ArrayList<EstimatePreviewRequest.Item>();int index=0;
            for(var c:product.components()) components.add(new EstimatePreviewRequest.Item("component-"+(index++),c.kind(),c.product(),c.mounting()));
            list.add(new Priced(product,preview.preview(new EstimatePreviewRequest(components),auth)));
        }
        return list;
    }
    public View create(CalculatedOrderRequest r,Authentication auth) {
        validate(r);var user=access(auth,r.branchName());
        // Shared with the legacy number allocator; serializes concurrent retry keys as well.
        long counter=db.queryForObject("SELECT last_temp_order_no FROM temp_order_counter WHERE id=1 FOR UPDATE",Long.class);
        var previous=db.queryForList("SELECT OrderNo,calculation_request_hash FROM orders WHERE calculation_request_key=? FOR UPDATE",r.requestKey());
        String hash=hash(r);
        if(!previous.isEmpty()) {
            if(!hash.equals(previous.get(0).get("calculation_request_hash"))) throw error(HttpStatus.CONFLICT,"REQUEST_KEY_REUSED","Цей ключ уже використано для іншого запиту");
            return read(((Number)previous.get(0).get("OrderNo")).longValue(),auth);
        }
        if(r.expectedRevision()!=0) throw bad("Нове замовлення повинно мати ревізію 0");
        var settings=commercial.settings(r.branchName(),auth);
        var priced=price(r,auth,settings.get("mode").toString());
        var ids=materialIds(priced);reservations.lockParts(ids);
        long orderNo=counter+1;
        db.update("UPDATE temp_order_counter SET last_temp_order_no=? WHERE id=1",orderNo);
        long branch=db.queryForObject("SELECT BranchNo FROM branches WHERE BranchName=?",Long.class,r.branchName());
        long customer=customer(r);
        var total=priced.stream().map(p->p.result().total()).reduce(BigDecimal.ZERO,BigDecimal::add);
        db.update("INSERT INTO orders(OrderNo,CustNo,BranchNo,EmpNo,SaleDate,ShipDate,ItemsTotal,AmountDueN,AmountPaid,Income,StatusOrder,Notice,PublicId,calculation_revision,calculation_request_key,calculation_request_hash) VALUES(?,?,?,?,CURRENT_TIMESTAMP,?,?,?,0,0,1,?,?,1,?,?)",
                orderNo,customer,branch,user.getId(),date(r.dueDate()),total,total,r.notice(),UUID.randomUUID().toString(),r.requestKey(),hash);
        db.update("UPDATE orders SET commerce_mode=?,commerce_partner_id=? WHERE OrderNo=?",settings.get("mode"),settings.get("partnerId"),orderNo);
        persist(orderNo,1,priced,auth.getName());
        for(long id:ids) reservations.refillPart(id,auth.getName());
        if(orderEvents!=null) orderEvents.publishEvent(new com.example.baget.telegram.OrderCopyCreated(orderNo));
        return read(orderNo,auth);
    }
    private long customer(CalculatedOrderRequest r) {
        if(r.custNo()!=null) {
            var ids=db.queryForList("SELECT CustNo FROM customer WHERE CustNo=?",Long.class,r.custNo());
            if(ids.isEmpty()) throw bad("Клієнта не знайдено");
            return r.custNo();
        }
        // Never silently attach a partial telephone match to an unrelated customer.
        var dto=new CustomerDTO();dto.setCompany(r.company().trim());dto.setMobile(r.phone().trim());dto.setAddr1(r.address());
        long id=customers.create(dto);em.flush();return id;
    }
    public View update(long orderNo,CalculatedOrderRequest r,Authentication auth) {
        validate(r);var old=lock(orderNo,auth);editable(orderNo,old,r.expectedRevision());
        if(!old.branchName().equals(r.branchName()) || !Objects.equals(r.custNo(),old.custNo()))
            throw bad("Під час редагування конфігурації клієнт і філія залишаються незмінними");
        var mode=db.queryForObject("SELECT commerce_mode FROM orders WHERE OrderNo=?",String.class,orderNo);
        var priced=price(r,auth,mode);
        var ids=materialIds(priced);ids.addAll(reservations.orderParts(orderNo));reservations.lockParts(ids);
        reservations.release(orderNo,auth.getName());
        db.update("DELETE FROM items WHERE OrderNo=?",orderNo);
        db.update("UPDATE order_products SET active=0 WHERE order_no=? AND active=1",orderNo);
        long revision=old.revision()+1;
        persist(orderNo,revision,priced,auth.getName());
        var total=priced.stream().map(p->p.result().total()).reduce(BigDecimal.ZERO,BigDecimal::add);
        db.update("UPDATE orders SET ItemsTotal=?,AmountDueN=GREATEST(0,?-COALESCE(AmountPaid,0)),Notice=?,ShipDate=?,calculation_revision=? WHERE OrderNo=?",
                total,total,r.notice(),date(r.dueDate()),revision,orderNo);
        for(long id:ids) reservations.refillPart(id,auth.getName());
        return read(orderNo,auth);
    }
    public View cancel(long orderNo,long revision,Authentication auth) {
        var old=lock(orderNo,auth);editable(orderNo,old,revision);
        var ids=reservations.orderParts(orderNo);reservations.lockParts(ids);reservations.release(orderNo,auth.getName());
        db.update("UPDATE orders SET StatusOrder=5,calculation_revision=calculation_revision+1 WHERE OrderNo=?",orderNo);
        for(long id:ids) reservations.refillPart(id,auth.getName());
        // Payments and customer property are deliberately untouched.
        return read(orderNo,auth);
    }
    public View refill(long orderNo,Authentication auth) {
        var old=lock(orderNo,auth);
        if(!Set.of(1,2,3).contains(old.statusOrder())) throw bad("Замовлення скасоване");
        var ids=reservations.orderParts(orderNo);reservations.lockParts(ids);
        for(long id:ids) reservations.refillPart(id,auth.getName());
        return read(orderNo,auth);
    }
    private View lock(long orderNo,Authentication auth) {
        if(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,orderNo).isEmpty()) throw error(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Замовлення не знайдено");
        return read(orderNo,auth);
    }
    private void editable(long orderNo,View old,long revision) {
        if(old.revision()!=revision) throw error(HttpStatus.CONFLICT,"STALE_REVISION","Замовлення вже змінено. Відкрийте його повторно");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT workshop_mode FROM orders WHERE OrderNo=?",Boolean.class,orderNo))
                || old.statusOrder()!=1 || db.queryForObject("SELECT COUNT(*) FROM invoice_orders WHERE order_no=?",Long.class,orderNo)>0
                || db.queryForObject("SELECT COUNT(*) FROM items WHERE OrderNo=? AND COALESCE(Qty,0)<>0",Long.class,orderNo)>0)
            throw error(HttpStatus.CONFLICT,"ORDER_LOCKED","Редагування/скасування доступне лише до виробництва й виставлення рахунку. Фактичні витрати та фінансові операції потребують окремого узгодження");
    }
    public View read(long orderNo,Authentication auth) {
        db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR SHARE",Long.class,orderNo);
        var headers=db.query("SELECT o.calculation_revision,o.StatusOrder,b.BranchName,o.CustNo,c.Company,c.Mobile,c.Addr1,o.Notice,o.ShipDate,o.ItemsTotal FROM orders o JOIN branches b ON b.BranchNo=o.BranchNo JOIN customer c ON c.CustNo=o.CustNo WHERE o.OrderNo=?",
                (rs,n)->new View(orderNo,rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getDate(9)==null?null:rs.getDate(9).toLocalDate(),rs.getBigDecimal(10),List.of()),orderNo);
        if(headers.isEmpty()) throw error(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Замовлення не знайдено");
        var h=headers.get(0);access(auth,h.branchName());
        if(h.revision()==0) throw bad("Це замовлення створено у старій формі");
        var products=db.query("SELECT id,client_product_id,configuration,calculation,total FROM order_products WHERE order_no=? AND active=1 ORDER BY id",
                (rs,n)->new ProductView(rs.getLong(1),rs.getString(2),decode(rs.getString(3)),decode(rs.getString(4)),rs.getBigDecimal(5),reservations.balances(rs.getLong(1))),orderNo);
        return new View(h.orderNo(),h.revision(),h.statusOrder(),h.branchName(),h.custNo(),h.company(),h.phone(),h.address(),h.notice(),h.dueDate(),h.total(),products);
    }
    private TreeSet<Long> materialIds(List<Priced> priced) {
        var ids=new TreeSet<Long>();
        for(var p:priced) for(var c:p.result().items()) for(var l:lines(c)) {
            String kind=db.queryForObject("SELECT part_kind FROM parts WHERE PartNo=?",String.class,l.calculation().partNo());
            if(!"WORK".equals(kind)) ids.add(l.calculation().partNo());
        }
        return ids;
    }
    private static List<ProductPreviewResponse.Line> lines(EstimatePreviewResponse.Item c) { return c.product()!=null?c.product().lines():c.mounting().lines(); }
    private void persist(long orderNo,long revision,List<Priced> priced,String actor) {
        long itemNo=1;
        for(var p:priced) {
            db.update("INSERT INTO order_products(order_no,client_product_id,revision,configuration,calculation,total,tracking_id) VALUES(?,?,?,?,?,?,?)",
                    orderNo,p.draft().clientProductId(),revision,encode(Map.of("schemaVersion",1,"draft",p.draft())),encode(p.result()),p.result().total(),UUID.randomUUID().toString());
            long productId=db.queryForObject("SELECT id FROM order_products WHERE order_no=? AND revision=? AND client_product_id=?",Long.class,orderNo,revision,p.draft().clientProductId());
            var demand=new TreeMap<Long,BigDecimal>();
            for(int ci=0;ci<p.result().items().size();ci++) {
                var component=p.result().items().get(ci);var componentLines=lines(component);
                var amounts=AmountAllocation.reconcile(componentLines.stream().map(l->l.calculation().amount()).toList(),component.total());
                var source=p.draft().components().get(ci);
                BigDecimal width=source.product()!=null?source.product().widthMm():source.mounting().widthMm();
                BigDecimal height=source.product()!=null?source.product().heightMm():source.mounting().heightMm();
                if(source.product()!=null && source.product().matMargins()!=null) {
                    var m=source.product().matMargins();width=width.add(m.leftMm()).add(m.rightMm());height=height.add(m.topMm()).add(m.bottomMm());
                }
                for(int li=0;li<componentLines.size();li++) {
                    var line=componentLines.get(li);var c=line.calculation();
                    var meta=db.queryForMap("SELECT part_kind,ProfilWidth,Cost FROM parts WHERE PartNo=?",c.partNo());
                    db.update("INSERT INTO items(OrderNo,ItemNo,PartNo,ProfilWidth,EstimationWidth,EstimationHeight,Qty,Quantity,SellPrice,Discount,Cost,`Sum`,order_product_id,planned_consumption,calculated_amount,calculation_role,calculation_unit,stock_material,billing_unit_price) VALUES(?,?,?,?,?,?,0,?,?,0,?,?,?,?,?,?,?,?,?)",
                            orderNo,itemNo++,c.partNo(),"FRAME".equals(line.role())?meta.get("ProfilWidth"):null,width.movePointLeft(3),height.movePointLeft(3),c.productQuantity(),c.unitPrice(),meta.get("Cost"),amounts.get(li),productId,c.totalConsumption(),amounts.get(li),line.role(),c.unit().name(),!"WORK".equals(meta.get("part_kind")),c.unitPrice());
                    if(!"WORK".equals(meta.get("part_kind"))) demand.merge(c.partNo(),c.totalConsumption(),BigDecimal::add);
                }
            }
            commercial.initialize(orderNo,productId,actor);
            demand.forEach((part,qty)->reservations.add(productId,part,qty,actor));
        }
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("JSON serialization",e);} }
    private JsonNode decode(String value) {try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("Saved JSON",e);} }
    private String hash(Object value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private static Date date(LocalDate d) {return d==null?null:Date.valueOf(d);}
    private static PreviewException bad(String msg) {return error(HttpStatus.BAD_REQUEST,"INVALID_CALCULATED_ORDER",msg);}
    private static PreviewException error(HttpStatus status,String code,String msg) {return new PreviewException(status,code,msg);}
}
