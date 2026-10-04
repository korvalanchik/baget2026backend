package com.example.baget.crm;
/** Read-only predicates. Enterprise invoice debt and reseller reference money stay separate. */
final class CrmSettlementSql {
 private CrmSettlementSql() {}
 static final String COMPANY="""
 (EXISTS(SELECT 1 FROM branch_commercial_settings cs WHERE cs.partner_id=r.customerId)
 OR EXISTS(SELECT 1 FROM orders co WHERE co.commerce_partner_id=r.customerId)
 OR EXISTS(SELECT 1 FROM invoice_commercial_owner ic WHERE ic.partner_id=r.customerId))
 """;
 private static final String INVOICE="""
 SELECT 1 FROM invoices fi WHERE fi.customer_id=r.customerId
 AND fi.lifecycle='ACTIVE' AND fi.status NOT IN ('DRAFT','CANCELLED')
 AND NOT EXISTS(SELECT 1 FROM invoice_commercial_owner ic WHERE ic.invoice_id=fi.id)
 AND EXISTS(SELECT 1 FROM invoice_orders fio JOIN orders fo ON fo.OrderNo=fio.order_no WHERE fio.invoice_id=fi.id AND fo.BranchNo=r.branchNo)
 AND (SELECT COALESCE(SUM(CASE WHEN le.direction='OUT' THEN le.amount ELSE -le.amount END),0) FROM ledger_entries le WHERE le.invoice_id=fi.id)>0
 """;
 static final String OWN_DEBT="EXISTS("+INVOICE+")";
 static final String OVERDUE="EXISTS("+INVOICE+" AND EXISTS(SELECT 1 FROM invoice_payment_terms ft WHERE ft.invoice_id=fi.id AND ft.due_date<:today))";
 static final String OWN_ADVANCE="""
 ((SELECT COALESCE(SUM(ct.amount),0) FROM customer_transactions ct WHERE ct.customer_id=r.customerId AND ct.active=1 AND ct.type IN ('ADVANCE','ADVANCE_ALLOCATION'))>0
 AND (EXISTS(SELECT 1 FROM customer_transactions ca WHERE ca.customer_id=r.customerId AND ca.branch_id=r.branchNo AND ca.active=1)
 OR EXISTS(SELECT 1 FROM orders ao WHERE ao.CustNo=r.customerId AND ao.BranchNo=r.branchNo AND COALESCE(ao.commerce_mode,'OWN')='OWN')))
 """;
 static final String REFERENCE_DEBT="""
 EXISTS(SELECT 1 FROM orders ro WHERE ro.CustNo=r.customerId AND ro.BranchNo=r.branchNo
 AND ro.commerce_mode='RESELLER' AND ro.StatusOrder NOT IN (5,10)
 AND (SELECT SUM(CASE WHEN p.production_confirmed=1 THEN p.actual_total WHEN pa.state IN ('AUTO','APPROVED') THEN pa.current_retail ELSE p.total END)
 FROM order_products p LEFT JOIN product_price_approval pa ON pa.product_id=p.id WHERE p.order_no=ro.OrderNo AND p.active=1)
 > (SELECT COALESCE(SUM(pm.paid_delta),0) FROM partner_customer_movements pm WHERE pm.order_no=ro.OrderNo AND pm.customer_id=ro.CustNo AND pm.partner_id=ro.commerce_partner_id))
 """;
 static final String REFERENCE_ADVANCE="""
 EXISTS(SELECT 1 FROM partner_customer_movements pm WHERE pm.customer_id=r.customerId
 AND (EXISTS(SELECT 1 FROM orders po WHERE po.CustNo=r.customerId AND po.BranchNo=r.branchNo AND po.commerce_partner_id=pm.partner_id AND po.commerce_mode='RESELLER')
 OR EXISTS(SELECT 1 FROM branch_commercial_settings ps WHERE ps.branch_no=r.branchNo AND ps.mode='RESELLER' AND ps.partner_id=pm.partner_id))
 GROUP BY pm.partner_id HAVING SUM(pm.advance_delta)>0)
 """;
 static final String DEBT="("+OWN_DEBT+" OR "+REFERENCE_DEBT+")";
 static final String ADVANCE="("+OWN_ADVANCE+" OR "+REFERENCE_ADVANCE+")";
 static String condition(String tab){return "NOT "+COMPANY+" AND "+switch(tab){case "DEBT"->DEBT;case "ADVANCE"->ADVANCE;case "OVERDUE"->OVERDUE;default->"("+DEBT+" OR "+ADVANCE+")";};}
 static final String COLUMNS=", "+OWN_DEBT+" AS ownDebt, "+OWN_ADVANCE+" AS ownAdvance, "+REFERENCE_DEBT+" AS referenceDebt, "+REFERENCE_ADVANCE+" AS referenceAdvance ";
}
