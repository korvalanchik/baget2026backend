package com.example.baget.reports;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.math.*;
import java.util.*;
@RestController
@RequestMapping("/api/reports/partner-results")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PartnerResultsController {
 private final JdbcTemplate db;private final UsersRepository users;
 public PartnerResultsController(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"REPORT_ACCESS","Звіт доступний адміністратору");}
 @GetMapping("/options") public Object options(Authentication a){access(a);return Map.of("partners",db.queryForList("SELECT DISTINCT c.CustNo AS id,c.Company AS name FROM invoice_commercial_owner co JOIN customer c ON c.CustNo=co.partner_id ORDER BY c.Company"),"branches",db.queryForList("SELECT DISTINCT b.BranchNo AS id,b.BranchName AS name FROM branches b JOIN orders o ON o.BranchNo=b.BranchNo WHERE o.commerce_mode='RESELLER' ORDER BY b.BranchName"));}
 static final String SALES="""
 WITH originals AS (
 SELECT ip.invoice_id,ip.product_id,ip.order_no,ip.billed_amount,i.invoice_no,i.created_at,co.partner_id
 FROM invoice_products ip JOIN invoices i ON i.id=ip.invoice_id
 JOIN invoice_commercial_owner co ON co.invoice_id=i.id AND co.partner_id=i.customer_id
 JOIN orders o ON o.OrderNo=ip.order_no
 WHERE ip.source_invoice_id IS NULL AND i.status NOT IN ('DRAFT','CANCELLED')
 AND i.created_at>=? AND i.created_at<? AND (? IS NULL OR co.partner_id=?) AND (? IS NULL OR o.BranchNo=?)
 ), costs AS (
 SELECT it.order_product_id AS productId,
 SUM(CASE WHEN m.quantity_delta<0 AND m.inventory_unit_cost IS NOT NULL THEN -m.quantity_delta*m.inventory_unit_cost ELSE 0 END) AS materialAmount,
 SUM(CASE WHEN m.id IS NULL OR m.inventory_unit_cost IS NULL THEN 1 ELSE 0 END) AS unknownMaterials
 FROM items it LEFT JOIN inventory_movements m ON m.order_no=it.OrderNo AND m.item_no=it.ItemNo AND m.quantity_delta<0
 WHERE it.stock_material=1 AND it.actual_consumption>0 AND EXISTS(SELECT 1 FROM originals orig WHERE orig.product_id=it.order_product_id)
 GROUP BY it.order_product_id
 )
 SELECT orig.invoice_id AS invoiceId,orig.invoice_no AS invoiceNo,orig.created_at AS invoicedAt,orig.product_id AS productId,
 orig.order_no AS orderNo,p.client_product_id AS productName,orig.partner_id AS partnerId,c.Company AS partnerName,b.BranchName AS branchName,
 orig.billed_amount AS saleAmount,COALESCE(costs.materialAmount,0) AS materialAmount,COALESCE(costs.unknownMaterials,0) AS unknownMaterials,
 0 AS rewardAmount,NULL AS rewardState
 FROM originals orig JOIN order_products p ON p.id=orig.product_id JOIN orders o ON o.OrderNo=orig.order_no
 JOIN branches b ON b.BranchNo=o.BranchNo JOIN customer c ON c.CustNo=orig.partner_id LEFT JOIN costs ON costs.productId=orig.product_id
 ORDER BY orig.created_at DESC,orig.invoice_id DESC,orig.product_id LIMIT 10001
 """;
 static final String DEBTS="""
 SELECT co.partner_id AS partnerId,c.Company AS partnerName,SUM(GREATEST(COALESCE(l.debt,0),0)) AS debt
 FROM invoices i JOIN invoice_commercial_owner co ON co.invoice_id=i.id AND co.partner_id=i.customer_id
 JOIN customer c ON c.CustNo=co.partner_id
 LEFT JOIN (SELECT invoice_id,SUM(CASE WHEN direction='OUT' THEN amount ELSE -amount END) AS debt FROM ledger_entries WHERE invoice_id IS NOT NULL GROUP BY invoice_id) l ON l.invoice_id=i.id
 WHERE i.lifecycle='ACTIVE' AND i.status NOT IN ('DRAFT','CANCELLED') AND (? IS NULL OR co.partner_id=?)
 GROUP BY co.partner_id,c.Company ORDER BY c.Company,co.partner_id
 """;
 @GetMapping public Object report(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="partner",required=false) Long partner,@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="page",defaultValue="0") int page,Authentication a){
  access(a);if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365||page<-1||page>200||partner!=null&&partner<=0||branch!=null&&branch<=0)throw new PreviewException(HttpStatus.BAD_REQUEST,"REPORT_FILTER","Вкажіть коректний період до 366 днів і фільтри");
  var zone=ZoneId.of("Europe/Kyiv");var rows=db.queryForList(SALES,Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()),partner,partner,branch,branch);
  if(rows.size()>10000)throw new PreviewException(HttpStatus.BAD_REQUEST,"REPORT_LIMIT","Понад 10000 позицій. Зменшіть період або оберіть фірму/філію");
  var summary=OwnerResultsMath.summarize(rows);summary.put("invoiceCount",rows.stream().map(r->r.get("invoiceId")).distinct().count());
  var debts=db.queryForList(DEBTS,partner,partner);BigDecimal debtTotal=debts.stream().map(r->new BigDecimal(r.get("debt").toString())).reduce(BigDecimal.ZERO,BigDecimal::add);
  int start=page<0?0:Math.min(page*50,rows.size()),end=page<0?rows.size():Math.min(start+50,rows.size());
  return Map.of("summary",summary,"rows",rows.subList(start,end),"hasMore",end<rows.size(),"page",page,"debts",debts,"debtTotal",debtTotal,"from",from,"to",to);
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
}
