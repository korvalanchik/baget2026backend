package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Global stock, matching the existing Parts.OnHand model. All allocators lock Parts in ID order. */
@Service
public class InventoryReservations {
    private final JdbcTemplate db;
    public InventoryReservations(JdbcTemplate db) { this.db=db; }
    public record Balance(long partNo, String description, BigDecimal demand, BigDecimal reserved,
                          BigDecimal shortage, boolean stockKnown, String unit) {}
    public void lockParts(Collection<Long> ids) {
        for(long id:new TreeSet<>(ids)) db.queryForObject("SELECT PartNo FROM parts WHERE PartNo=? FOR UPDATE",Long.class,id);
    }
    private record Row(long id, BigDecimal demand, BigDecimal reserved) {}
    @Transactional
    public void refillPart(long partNo, String actor) {
        var stock=db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=? FOR UPDATE",BigDecimal.class,partNo);
        // Locking reads see the latest commits even under MySQL REPEATABLE READ.
        var rows=db.query("SELECT id,demand,reserved FROM material_reservations WHERE part_no=? AND active=1 ORDER BY id FOR UPDATE",
                (rs,n)->new Row(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)),partNo);
        var used=rows.stream().map(Row::reserved).reduce(BigDecimal.ZERO,BigDecimal::add);
        if ((stock==null && used.signum()>0) || (stock!=null && stock.max(BigDecimal.ZERO).compareTo(used)<0))
            throw new PreviewException(HttpStatus.CONFLICT,"STOCK_BELOW_RESERVED","Залишок матеріалу "+partNo+" менший за чинний резерв. Потрібне узгодження резервів.");
        for(var row:rows) {
            var added=ReservationMath.allocate(stock,used,row.demand().subtract(row.reserved()));
            if(added.signum()>0) {
                change(row.id(),row.reserved(),row.reserved().add(added),"ALLOCATE",actor);
                used=used.add(added);
            }
        }
    }
    public void add(long productId,long partNo,BigDecimal demand,String actor) {
        db.update("INSERT INTO material_reservations(order_product_id,part_no,demand,reserved) VALUES(?,?,?,0)",productId,partNo,demand);
        var id=db.queryForObject("SELECT id FROM material_reservations WHERE order_product_id=? AND part_no=?",Long.class,productId,partNo);
        db.update("INSERT INTO reservation_events(reservation_id,reserved_before,reserved_after,reason,actor) VALUES(?,0,0,'DEMAND',?)",id,actor);
    }
    public Set<Long> orderParts(long orderNo) {
        return new TreeSet<>(db.queryForList("SELECT DISTINCT r.part_no FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE p.order_no=? AND r.active=1",Long.class,orderNo));
    }
    public void release(long orderNo,String actor) {
        var rows=db.query("SELECT r.id,r.demand,r.reserved FROM material_reservations r JOIN order_products p ON p.id=r.order_product_id WHERE p.order_no=? AND r.active=1 ORDER BY r.part_no,r.id FOR UPDATE",
                (rs,n)->new Row(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)),orderNo);
        for(var row:rows) {
            change(row.id(),row.reserved(),BigDecimal.ZERO,"RELEASE",actor);
            db.update("UPDATE material_reservations SET active=0 WHERE id=?",row.id());
        }
    }
    /** Caller holds the order lock and all affected Parts locks. Other products keep their reservations. */
    public void releaseProduct(long productId,String actor) {
        var rows=db.query("SELECT id,demand,reserved FROM material_reservations WHERE order_product_id=? AND active=1 ORDER BY part_no,id FOR UPDATE",
                (rs,n)->new Row(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)),productId);
        for(var row:rows) {
            change(row.id(),row.reserved(),BigDecimal.ZERO,"PRODUCTION",actor);
            db.update("UPDATE material_reservations SET active=0 WHERE id=?",row.id());
        }
    }
    /** Caller holds order and all relevant Parts locks. Demand is ONLY the current rework's extra usage. */
    public void reworkDemand(long productId,long partNo,BigDecimal demand,String actor) {
        if(demand==null || demand.signum()<0) throw new IllegalArgumentException("Invalid rework demand");
        var rows=db.query("SELECT id,demand,reserved FROM material_reservations WHERE order_product_id=? AND part_no=? FOR UPDATE",
                (rs,n)->new Row(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)),productId,partNo);
        if(rows.isEmpty()) {
            if(demand.signum()>0) add(productId,partNo,demand,actor);
            return;
        }
        var row=rows.get(0);var keep=row.reserved().min(demand);
        // Update demand/reserved together to satisfy reserved <= demand at every SQL statement.
        db.update("UPDATE material_reservations SET demand=?,reserved=?,active=? WHERE id=?",demand,keep,demand.signum()>0,row.id());
        db.update("INSERT INTO reservation_events(reservation_id,reserved_before,reserved_after,reason,actor) VALUES(?,?,?,'REWORK_DEMAND',?)",
                row.id(),row.reserved(),keep,actor);
    }
    private void change(long id,BigDecimal before,BigDecimal after,String reason,String actor) {
        db.update("UPDATE material_reservations SET reserved=? WHERE id=?",after,id);
        db.update("INSERT INTO reservation_events(reservation_id,reserved_before,reserved_after,reason,actor) VALUES(?,?,?,?,?)",id,before,after,reason,actor);
    }
    public List<Balance> balances(long productId) {
        return db.query("SELECT r.part_no,p.Description,r.demand,r.reserved,p.OnHand,COALESCE(p.unit_type,CASE p.InQuality WHEN 1 THEN 'SQUARE_METER' WHEN 2 THEN 'METER' WHEN 3 THEN 'PIECE' ELSE '' END) FROM material_reservations r JOIN parts p ON p.PartNo=r.part_no WHERE r.order_product_id=? AND r.active=1 ORDER BY r.part_no",
                (rs,n)->new Balance(rs.getLong(1),rs.getString(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(3).subtract(rs.getBigDecimal(4)),rs.getObject(5)!=null,rs.getString(6)),productId);
    }
}
