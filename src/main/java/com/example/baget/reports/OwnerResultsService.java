package com.example.baget.reports;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class OwnerResultsService {
 private final JdbcTemplate db;private final UsersRepository users;
 public OwnerResultsService(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"REPORT_ACCESS","Звіт доступний адміністратору");}
 private PreviewException bad(String message){return new PreviewException(HttpStatus.BAD_REQUEST,"REPORT_FILTER",message);}
 public Object branches(Authentication a){access(a);return db.queryForList("SELECT DISTINCT b.BranchNo AS id,b.BranchName AS name FROM branches b JOIN orders o ON o.BranchNo=b.BranchNo WHERE o.commerce_mode='OWN' ORDER BY b.BranchName");}
 static final String SQL="""
 WITH issued AS (
  SELECT product_id,MIN(created_at) AS issuedAt FROM product_transfers
  WHERE action='ACCEPT' AND to_location='CUSTOMER' GROUP BY product_id
 ), selected AS (
  SELECT p.id,p.order_no,p.client_product_id,o.BranchNo,d.issuedAt
  FROM issued d JOIN order_products p ON p.id=d.product_id JOIN orders o ON o.OrderNo=p.order_no
  WHERE o.commerce_mode='OWN' AND d.issuedAt>=? AND d.issuedAt<? AND (? IS NULL OR o.BranchNo=?)
 ), costs AS (
  SELECT s.id,
   SUM(CASE WHEN m.quantity_delta<0 AND m.inventory_unit_cost IS NOT NULL THEN -m.quantity_delta*m.inventory_unit_cost ELSE 0 END) AS materialAmount,
   SUM(CASE WHEN m.id IS NULL OR m.inventory_unit_cost IS NULL THEN 1 ELSE 0 END) AS unknownMaterials
  FROM selected s JOIN items it ON it.order_product_id=s.id AND it.stock_material=1 AND it.actual_consumption>0
  LEFT JOIN product_reworks rw ON rw.id=it.rework_id
  LEFT JOIN inventory_movements m ON m.order_no=it.OrderNo AND m.item_no=it.ItemNo AND m.created_at<=s.issuedAt AND m.quantity_delta<0
  WHERE it.rework_id IS NULL OR rw.completed_at<=s.issuedAt
  GROUP BY s.id
 ), earned AS (
  SELECT e.product_id,SUM(e.amount) AS rewardAmount FROM reward_entries e JOIN selected s ON s.id=e.product_id
  WHERE e.kind IN ('ACCRUAL','CORRECTION') GROUP BY e.product_id
 )
 SELECT s.id AS productId,s.order_no AS orderNo,s.client_product_id AS productName,s.BranchNo AS branchNo,b.BranchName AS branchName,s.issuedAt,
  ip.billed_amount AS saleAmount,COALESCE(c.materialAmount,0) AS materialAmount,COALESCE(c.unknownMaterials,0) AS unknownMaterials,
  COALESCE(e.rewardAmount,0) AS rewardAmount,ri.state AS rewardState
 FROM selected s JOIN branches b ON b.BranchNo=s.BranchNo
 LEFT JOIN product_invoice_claims claim ON claim.product_id=s.id
 LEFT JOIN invoices inv ON inv.id=claim.invoice_id AND inv.lifecycle='ACTIVE' AND inv.status NOT IN ('DRAFT','CANCELLED')
 LEFT JOIN invoice_products ip ON ip.invoice_id=inv.id AND ip.product_id=s.id
 LEFT JOIN costs c ON c.id=s.id LEFT JOIN earned e ON e.product_id=s.id LEFT JOIN reward_issues ri ON ri.product_id=s.id
 ORDER BY s.issuedAt DESC,s.id DESC LIMIT 10001
 """;
 public Object report(LocalDate from,LocalDate to,Long branch,int page,Authentication a){access(a);
  if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365||page<0||page>200||branch!=null&&branch<=0)throw bad("Вкажіть період до 366 днів та коректну філію");
  var zone=ZoneId.of("Europe/Kyiv");var rows=db.queryForList(SQL,Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()),branch,branch);
  if(rows.size()>10000)throw bad("У звіті понад 10000 виробів. Зменшіть період або оберіть філію");
  var summary=OwnerResultsMath.summarize(rows);int start=Math.min(page*50,rows.size()),end=Math.min(start+50,rows.size());
  return Map.of("summary",summary,"rows",rows.subList(start,end),"hasMore",end<rows.size(),"page",page,"from",from,"to",to);
 }
}
