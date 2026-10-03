package com.example.baget.billing;
import com.example.baget.reconciliation.PartnerReconciliationService;
import com.example.baget.customer.CustomerTransactionRepository;
import com.example.baget.invoices.InvoiceRepository;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PartnerReceivablesService {
 private final JdbcTemplate db;private final PartnerReconciliationService reconciliation;private final CustomerTransactionRepository transactions;private final InvoiceRepository invoices;
 public PartnerReceivablesService(JdbcTemplate db,PartnerReconciliationService reconciliation,CustomerTransactionRepository transactions,InvoiceRepository invoices){this.db=db;this.reconciliation=reconciliation;this.transactions=transactions;this.invoices=invoices;}
 static final String BASE="""
 FROM invoices i
 JOIN invoice_commercial_owner co ON co.invoice_id=i.id AND co.partner_id=i.customer_id
 JOIN customer c ON c.CustNo=co.partner_id
 JOIN (
  SELECT le.invoice_id,
   SUM(CASE WHEN le.direction='OUT' THEN le.amount ELSE -le.amount END) AS debt,
   SUM(CASE WHEN le.direction='IN' AND le.category='PAYMENT_RECEIVED' THEN le.amount ELSE 0 END) AS paid,
   SUM(CASE WHEN le.direction='IN' AND le.category='INTERNAL_TRANSFER_IN' AND tx.type='ADVANCE_ALLOCATION' THEN le.amount ELSE 0 END) AS allocated
  FROM ledger_entries le LEFT JOIN customer_transactions tx ON tx.id=le.customer_tx_id
  WHERE le.invoice_id IS NOT NULL GROUP BY le.invoice_id
 ) l ON l.invoice_id=i.id
 WHERE i.lifecycle='ACTIVE' AND i.status NOT IN ('DRAFT','CANCELLED') AND l.debt>0
 AND EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=i.id)
 """;
 public Map<String,Object> read(Long partner,int page,Authentication a){
  // Same complete-company access as reconciliation; never present a partial company debt as complete.
  var options=reconciliation.options(a);
  var ids=options.stream().map(p->((Number)p.get("id")).longValue()).filter(id->partner==null || id.equals(partner)).toList();
  if(partner!=null && ids.isEmpty())throw new PreviewException(HttpStatus.FORBIDDEN,"RECEIVABLES_ACCESS","Фірма недоступна для повного перегляду розрахунків");
  int safePage=Math.max(0,Math.min(page,100000));
  var rows=new ArrayList<Map<String,Object>>();var groups=new ArrayList<Map<String,Object>>();boolean hasNext=false;
  if(!ids.isEmpty()){
   String filter=" AND co.partner_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
   groups.addAll(db.queryForList("SELECT co.partner_id AS partnerId,c.Company AS partnerName,COUNT(*) AS invoiceCount,SUM(l.debt) AS debt "+BASE+filter+" GROUP BY co.partner_id,c.Company ORDER BY c.Company,co.partner_id",ids.toArray()));
   var args=new ArrayList<Object>(ids);args.add(safePage*50);
   rows.addAll(db.queryForList("SELECT i.id,i.invoice_no AS invoiceNo,co.partner_id AS partnerId,c.Company AS partnerName,i.total_amount AS amount,l.paid,l.allocated,l.debt "+BASE+filter+" ORDER BY i.created_at,i.id LIMIT 51 OFFSET ?",args.toArray()));
   hasNext=rows.size()>50;if(hasNext)rows.remove(rows.size()-1);
   var dates=new HashMap<Long,OffsetDateTime>();
   for(var invoice:invoices.findAllById(rows.stream().map(r->((Number)r.get("id")).longValue()).toList()))dates.put(invoice.getId(),invoice.getCreatedAt());
   LocalDate today=LocalDate.now(InvoiceAge.ZONE);
   for(var row:rows){var issued=dates.get(((Number)row.get("id")).longValue());if(issued==null)throw new PreviewException(HttpStatus.CONFLICT,"RECEIVABLES_CHANGED","Рахунок змінився. Оновіть реєстр");row.put("issuedDate",issued.atZoneSameInstant(InvoiceAge.ZONE).toLocalDate().toString());row.put("ageDays",InvoiceAge.days(issued,today));}
   for(var group:groups)group.put("availableAdvance",transactions.calculateAvailableAdvance(((Number)group.get("partnerId")).longValue()));
   // A selected company can have advance even if it currently has no unpaid invoices.
   if(partner!=null && groups.isEmpty()){var g=new HashMap<String,Object>();g.put("partnerId",partner);g.put("partnerName",options.stream().filter(p->((Number)p.get("id")).longValue()==partner).findFirst().orElseThrow().get("name"));g.put("invoiceCount",0);g.put("debt",java.math.BigDecimal.ZERO);g.put("availableAdvance",transactions.calculateAvailableAdvance(partner));groups.add(g);}
  }
  var result=new LinkedHashMap<String,Object>();result.put("partners",options);result.put("groups",groups);result.put("rows",rows);result.put("page",safePage);result.put("hasNext",hasNext);result.put("asOf",ZonedDateTime.now(InvoiceAge.ZONE).toOffsetDateTime().toString());return result;
 }
}
