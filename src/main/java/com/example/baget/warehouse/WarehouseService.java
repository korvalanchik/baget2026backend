package com.example.baget.warehouse;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.sql.Types;
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
    /**
     * Creates a material-catalog item with validated calculation metadata.
     * Physical stock starts at zero; real stock must enter through inventory count or receipt workflows.
     */
    public Map<String,Object> createMaterial(NewMaterial request, Authentication authentication) {
        stockAccess(authentication);

        String description = request.description() == null ? "" : request.description().trim();
        if (description.isEmpty() || description.length() > 30) {
            throw conflict("Артикул / назва має містити від 1 до 30 символів");
        }
        if (!db.queryForList(
                "SELECT PartNo FROM parts WHERE LOWER(TRIM(Description))=LOWER(TRIM(?)) LIMIT 1",
                Long.class, description).isEmpty()) {
            throw new PreviewException(HttpStatus.CONFLICT, "MATERIAL_EXISTS",
                    "Такий артикул уже існує. Знайдіть його в каталозі.");
        }
        if (request.vendorNo() != null && db.queryForList(
                "SELECT VendorNo FROM vendors WHERE VendorNo=?", Long.class, request.vendorNo()).isEmpty()) {
            throw conflict("Обраного постачальника не знайдено");
        }

        UnitType expectedUnit = unitFor(request.calculationMethod());
        if (request.unit() != expectedUnit) {
            throw conflict("Одиниця не відповідає методу розрахунку: " + request.calculationMethod());
        }

        Map<String,BigDecimal> params = new LinkedHashMap<>();
        BigDecimal profileWidthMeters = null;
        switch (request.calculationMethod()) {
            case AREA -> {
                rejectParams(request.marginWidthMm(), request.marginHeightMm(), request.allowanceMm(),
                        request.railWidthMm(), request.profileWidthMm());
            }
            case AREA_WITH_MARGIN -> {
                rejectUnused(request.allowanceMm(), request.railWidthMm(), request.profileWidthMm());
                params.put("marginWidthMm", requiredNonNegative(request.marginWidthMm(), "Припуск по ширині"));
                params.put("marginHeightMm", requiredNonNegative(request.marginHeightMm(), "Припуск по висоті"));
            }
            case PERIMETER -> {
                rejectUnused(request.marginWidthMm(), request.marginHeightMm(), request.allowanceMm(),
                        request.profileWidthMm());
                if (request.railWidthMm() != null) params.put("railWidthMm", request.railWidthMm());
            }
            case FRAME_PROFILE -> {
                rejectUnused(request.marginWidthMm(), request.marginHeightMm(), request.allowanceMm(),
                        request.railWidthMm());
                BigDecimal width = request.profileWidthMm();
                if (width == null || width.compareTo(new BigDecimal("0.300")) <= 0) {
                    throw conflict("Для профілю вкажіть ширину понад 0,3 мм");
                }
                profileWidthMeters = width.divide(new BigDecimal("1000"), 9, RoundingMode.HALF_UP);
            }
            case WIDTH_PLUS_ALLOWANCE -> {
                rejectUnused(request.marginWidthMm(), request.marginHeightMm(), request.railWidthMm(),
                        request.profileWidthMm());
                params.put("allowanceMm", requiredNonNegative(request.allowanceMm(), "Припуск"));
            }
            case QUANTITY -> {
                rejectParams(request.marginWidthMm(), request.marginHeightMm(), request.allowanceMm(),
                        request.railWidthMm(), request.profileWidthMm());
            }
            case FIXED -> throw conflict(
                    "Метод FIXED призначений для робіт. Для матеріалу оберіть «Кількість на виріб».");
        }

        validatePrice(request.cost(), "Cost");
        validatePrice(request.listPrice(), "ListPrice");
        validatePrice(request.listPrice1(), "ListPrice_1");
        validatePrice(request.listPrice3(), "ListPrice_3");

        final Double storedProfileWidth = profileWidthMeters == null ? null : profileWidthMeters.doubleValue();
        final int legacyUnitQuality = switch (request.unit()) {
            case SQUARE_METER -> 1;
            case METER -> 2;
            case PIECE -> 3;
        };
        final String calculationParams = json.valueToTree(params).toString();
        String sql = """
            INSERT INTO parts (
                VendorNo, Description, ProfilWidth, InQuality, OnHand, OnOrder,
                Cost, ListPrice, ListPrice_1, ListPrice_2, NoPercent, ListPrice_3, Version,
                unit_type, calculation_method, part_kind, calculation_params
            ) VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, NULL, 0, ?, 0, ?, ?, 'MATERIAL', CAST(? AS JSON))
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statement = connection -> {
            var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            if (request.vendorNo() == null) ps.setNull(index++, Types.BIGINT);
            else ps.setLong(index++, request.vendorNo());
            ps.setString(index++, description);
            if (storedProfileWidth == null) ps.setNull(index++, Types.DOUBLE);
            else ps.setDouble(index++, storedProfileWidth);
            ps.setInt(index++, legacyUnitQuality);
            ps.setDouble(index++, request.cost().doubleValue());
            ps.setDouble(index++, request.listPrice().doubleValue());
            ps.setDouble(index++, request.listPrice1().doubleValue());
            ps.setDouble(index++, request.listPrice3().doubleValue());
            ps.setString(index++, request.unit().name());
            ps.setString(index++, request.calculationMethod().name());
            ps.setString(index, calculationParams);
            return ps;
        };
        db.update(statement, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return the new material ID");

        long partNo = key.longValue();
        return Map.of(
                "partNo", partNo,
                "description", description,
                "unit", request.unit().name(),
                "calculationMethod", request.calculationMethod().name(),
                "onHand", BigDecimal.ZERO
        );
    }

    private UnitType unitFor(CalculationMethod method) {
        if (method == null) throw conflict("Оберіть метод розрахунку");
        return switch (method) {
            case AREA, AREA_WITH_MARGIN -> UnitType.SQUARE_METER;
            case PERIMETER, FRAME_PROFILE, WIDTH_PLUS_ALLOWANCE -> UnitType.METER;
            case QUANTITY -> UnitType.PIECE;
            case FIXED -> throw conflict("Метод FIXED не використовується для матеріалів");
        };
    }

    private BigDecimal requiredNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) throw conflict("Вкажіть коректне значення: " + label);
        return value;
    }

    private void validatePrice(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("999999999")) > 0) {
            throw conflict("Вкажіть коректну ціну " + label + " від 0 до 999999999");
        }
    }

    private void rejectUnused(BigDecimal... values) {
        if (Arrays.stream(values).anyMatch(Objects::nonNull)) {
            throw conflict("Приберіть параметри, які не використовуються обраним методом розрахунку");
        }
    }

    private void rejectParams(BigDecimal marginWidth, BigDecimal marginHeight, BigDecimal allowance,
                              BigDecimal railWidth, BigDecimal profileWidth) {
        rejectUnused(marginWidth, marginHeight, allowance, railWidth, profileWidth);
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
    public record NewMaterial(
        @NotBlank @Size(max=30) String description,
        Long vendorNo,
        @NotNull UnitType unit,
        @NotNull CalculationMethod calculationMethod,
        @DecimalMin("0.301") @DecimalMax("500") @Digits(integer=3,fraction=3) BigDecimal profileWidthMm,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4,fraction=3) BigDecimal marginWidthMm,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4,fraction=3) BigDecimal marginHeightMm,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer=4,fraction=3) BigDecimal allowanceMm,
        @DecimalMin("0.001") @DecimalMax("1000") @Digits(integer=4,fraction=3) BigDecimal railWidthMm,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal cost,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal listPrice,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal listPrice1,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal listPrice3
    ) {}
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
        if(accept && "CUSTOMER".equals(to)) markOrderReceivedIfAllProductsIssued(((Number)p.get("orderNo")).longValue());
        return tracking(token,a);
    }

    /**
     * Marks an order as received only after every active product has been accepted by the customer.
     * This runs inside the transfer transaction, while the order row is locked by transfer().
     */
    private void markOrderReceivedIfAllProductsIssued(long orderNo) {
        Long notIssued = db.queryForObject(
            "SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND (COALESCE(current_location,'') <> 'CUSTOMER' OR pending_location IS NOT NULL)",
            Long.class, orderNo);
        if(notIssued != null && notIssued == 0L) {
            db.update(
                "UPDATE orders SET StatusOrder=12, ClientReceivedDate=COALESCE(ClientReceivedDate,CURRENT_TIMESTAMP(6)) WHERE OrderNo=?",
                orderNo);
        }
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
        // Global StringTrimmerEditor(true) converts an empty search parameter to null.
        String query=search==null?"":search.trim();
        return db.queryForList("SELECT p.PartNo AS partNo,p.Description AS description,p.unit_type AS unit,CAST(p.OnHand AS DECIMAL(24,6)) AS onHand,COALESCE(r.demand,0) AS demand,COALESCE(r.reserved,0) AS reserved,GREATEST(0,COALESCE(r.demand,0)-COALESCE(r.reserved,0)) AS shortage FROM parts p LEFT JOIN (SELECT part_no,SUM(demand) demand,SUM(reserved) reserved FROM material_reservations WHERE active=1 GROUP BY part_no) r ON r.part_no=p.PartNo WHERE (p.part_kind='MATERIAL' OR r.part_no IS NOT NULL) AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0) AND (?=0 OR COALESCE(r.demand,0)>COALESCE(r.reserved,0)) ORDER BY p.PartNo LIMIT 100 OFFSET ?",query,query,shortageOnly?1:0,Math.max(0,Math.min(page,100000))*100);
    }
    public Map<String,Object> material(long id,Authentication a) {
        stockAccess(a);
        var p=db.queryForMap("SELECT PartNo AS partNo,Description AS description,unit_type AS unit,CAST(OnHand AS DECIMAL(24,6)) AS onHand,CASE WHEN inventory_cost_unit=unit_type THEN inventory_unit_cost ELSE NULL END AS inventoryUnitCost FROM parts WHERE PartNo=? AND part_kind='MATERIAL'",id);
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
        if("RECEIPT".equals(r.operation()))db.update("UPDATE parts SET inventory_unit_cost=NULL,inventory_cost_unit=NULL WHERE PartNo=?",id);
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
