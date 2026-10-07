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
import java.util.*;
@RestController
@RequestMapping("/api/reports/cash-receipts")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CashReceiptsController {
 private final JdbcTemplate db;private final UsersRepository users;
 public CashReceiptsController(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 private void access(Authentication a){if(a==null||!a.isAuthenticated()||!users.findByUsername(a.getName()).map(u->u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new PreviewException(HttpStatus.FORBIDDEN,"REPORT_ACCESS","Звіт доступний адміністратору");}
 static final String MONEY=" ((le.direction='IN' AND le.category IN ('PAYMENT_RECEIVED','CUSTOMER_ADVANCE')) OR (le.direction='OUT' AND le.category='CUSTOMER_REFUND')) ";
 static final String SELECT="""
 SELECT le.id,le.created_at AS createdAt,le.category,le.direction,le.amount,le.branch_id AS branchId,b.BranchName AS branchName,
 le.customer_id AS customerId,c.Company AS customerName,le.payer_id AS payerId,payer.Company AS payerName,
 le.invoice_id AS invoiceId,i.invoice_no AS invoiceNo,le.order_id AS orderNo,le.customer_tx_id AS transactionId,le.reference,le.note,u.username AS actor
 FROM ledger_entries le LEFT JOIN branches b ON b.BranchNo=le.branch_id
 LEFT JOIN customer c ON c.CustNo=le.customer_id LEFT JOIN customer payer ON payer.CustNo=le.payer_id
 LEFT JOIN invoices i ON i.id=le.invoice_id LEFT JOIN users u ON u.id=le.created_by
 """;
 @GetMapping("/branches") public Object branches(Authentication a){access(a);return db.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName");}
 @GetMapping public Object report(@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="page",defaultValue="0") int page,Authentication a){
  access(a);if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365||page<-1||page>200||branch!=null&&branch<=0)throw new PreviewException(HttpStatus.BAD_REQUEST,"REPORT_FILTER","Вкажіть період до 366 днів та коректну філію");
  var zone=ZoneId.of("Europe/Kyiv");var rows=db.queryForList(SELECT+" WHERE "+MONEY+" AND le.created_at>=? AND le.created_at<? AND (? IS NULL OR le.branch_id=?) ORDER BY le.created_at DESC,le.id DESC LIMIT 10001",Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()),branch,branch);
  if(rows.size()>10000)throw new PreviewException(HttpStatus.BAD_REQUEST,"REPORT_LIMIT","Понад 10000 проводок. Зменшіть період або оберіть філію");
  Map<String,Object> summary;try{summary=CashReceiptsMath.summarize(rows);}catch(IllegalArgumentException e){throw new PreviewException(HttpStatus.CONFLICT,"LEDGER_AMOUNT",e.getMessage());}
  int start=page<0?0:Math.min(page*50,rows.size()),end=page<0?rows.size():Math.min(start+50,rows.size());return Map.of("summary",summary,"rows",rows.subList(start,end),"hasMore",end<rows.size(),"page",page,"from",from,"to",to);
 }
 @GetMapping("/{id}") public Object detail(@PathVariable("id") long id,Authentication a){access(a);var rows=db.queryForList(SELECT+" WHERE le.id=? AND "+MONEY,id);if(rows.isEmpty())throw new PreviewException(HttpStatus.NOT_FOUND,"ENTRY_MISSING","Проводку не знайдено");var row=rows.get(0);var result=new LinkedHashMap<String,Object>();result.put("entry",row);
  result.put("customer",row.get("customerId")==null?List.of():db.queryForList("SELECT CustNo AS id,Company AS name,Mobile AS phone FROM customer WHERE CustNo=?",row.get("customerId")));
  result.put("invoice",row.get("invoiceId")==null?List.of():db.queryForList("SELECT id,invoice_no AS invoiceNo,total_amount AS amount,status,lifecycle,created_at AS createdAt FROM invoices WHERE id=?",row.get("invoiceId")));
  return result;
 }
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
}
