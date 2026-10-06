package com.example.baget.orderproduct;
import org.springframework.jdbc.core.JdbcTemplate;
/** Called immediately after the movement insert, within the production transaction,
 * while the Parts row is locked. One movement per item is enforced by the database. */
public final class ConsumptionCost {
 private ConsumptionCost(){}
 public static void capture(JdbcTemplate db,long orderNo,long itemNo){
  db.update("UPDATE inventory_movements m JOIN items i ON i.OrderNo=m.order_no AND i.ItemNo=m.item_no JOIN parts p ON p.PartNo=m.part_no SET m.inventory_unit_cost=CASE WHEN p.part_kind='MATERIAL' AND p.inventory_cost_unit=p.unit_type AND p.unit_type=i.calculation_unit AND p.inventory_unit_cost>=0 THEN p.inventory_unit_cost ELSE NULL END,m.inventory_cost_unit=i.calculation_unit,m.inventory_cost_captured_at=CURRENT_TIMESTAMP(6) WHERE m.order_no=? AND m.item_no=? AND m.quantity_delta<0 AND m.inventory_cost_captured_at IS NULL",orderNo,itemNo);
  db.update("UPDATE items i JOIN inventory_movements m ON m.order_no=i.OrderNo AND m.item_no=i.ItemNo SET i.inventory_unit_cost=m.inventory_unit_cost WHERE i.OrderNo=? AND i.ItemNo=? AND m.inventory_cost_captured_at IS NOT NULL",orderNo,itemNo);
 }
}
