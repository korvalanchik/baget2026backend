package com.example.baget.reconciliation;
import com.example.baget.billing.BillingAccess;
import com.example.baget.customer.CustomerTransactionRepository;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.User;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PartnerReconciliationService {
 private final JdbcTemplate db;private final BillingAccess access;private final ReconciliationLedgerRepository ledger;private final CustomerTransactionRepository transactions;
 public PartnerReconciliationService(JdbcTemplate db,BillingAccess access,ReconciliationLedgerRepository ledger,CustomerTransactionRepository transactions){this.db=db;this.access=access;this.ledger=ledger;this.transactions=transactions;}
 private static final String PARTNERS="SELECT c.CustNo AS id,c.Company AS name FROM customer c WHERE EXISTS(SELECT 1 FROM branch_commercial_settings s WHERE s.partner_id=c.CustNo) OR EXISTS(SELECT 1 FROM orders o WHERE o.commerce_partner_id=c.CustNo) OR EXISTS(SELECT 1 FROM invoice_commercial_owner co WHERE co.partner_id=c.CustNo)";
 private List<Long> branches(long partner){return db.queryForList("SELECT branch_id FROM ledger_entries WHERE customer_id=? UNION SELECT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no JOIN invoices i ON i.id=io.invoice_id WHERE i.customer_id=? UNION SELECT BranchNo FROM orders WHERE commerce_partner_id=? UNION SELECT branch_no FROM branch_commercial_settings WHERE partner_id=? UNION SELECT branch_id FROM customer_transactions WHERE customer_id=?",Long.class,partner,partner,partner,partner,partner);}
 private boolean completeAccess(User u,long partner){return access.admin(u) || branches(partner).stream().allMatch(id->u.getAllowedBranches().stream().anyMatch(b->b.getBranchNo().equals(id)));}
 public List<Map<String,Object>> options(Authentication a){var u=access.user(a);return db.queryForList(PARTNERS+" ORDER BY c.Company,c.CustNo").stream().filter(p->completeAccess(u,number(p,"id"))).toList();}
 public Map<String,Object> report(long partner,LocalDate from,LocalDate to,Authentication a){
  var u=access.user(a);
  try{ReconciliationMath.period(from,to);}catch(IllegalArgumentException e){throw fail(e.getMessage());}
  var firms=db.queryForList("SELECT p.id,p.name FROM ("+PARTNERS+") p WHERE p.id=?",partner);
  if(firms.isEmpty())throw fail("Фірму-посередника не знайдено");
  if(!completeAccess(u,partner))throw new PreviewException(HttpStatus.FORBIDDEN,"RECONCILIATION_ACCESS","Для повної звірки потрібен доступ до всіх філій фірми та філій обліку її проводок");
  var start=ReconciliationMath.start(from);var until=ReconciliationMath.start(to.plusDays(1));
  var entries=ledger.entries(partner,start,until,PageRequest.of(0,10001));
  if(entries.size()>10000)throw fail("Понад 10000 проводок. Скоротіть період звірки");
  var totals=ReconciliationMath.totals(ledger.balance(partner,start),entries);
  var ids=entries.stream().map(ReconciliationEntry::invoiceId).filter(Objects::nonNull).distinct().toList();
  if(ids.size()>1000)throw fail("Понад 1000 рахунків. Скоротіть період звірки");
  var invoices=new ArrayList<Map<String,Object>>();var byId=new HashMap<Long,Map<String,Object>>();
  for(int offset=0;offset<ids.size();offset+=200){
   var batch=ids.subList(offset,Math.min(offset+200,ids.size()));String marks=String.join(",",Collections.nCopies(batch.size(),"?"));
   var headers=db.queryForList("SELECT i.id,i.invoice_no AS invoiceNo,i.total_amount AS amount,i.lifecycle AS currentLifecycle,i.status AS currentStatus,i.created_at AS createdAt,co.partner_id AS commercialOwner FROM invoices i LEFT JOIN invoice_commercial_owner co ON co.invoice_id=i.id WHERE i.id IN ("+marks+") ORDER BY i.id",batch.toArray());
   for(var h:headers)if(!access.admin(u))access.invoice(u,number(h,"id"));
   var lines=db.queryForList("SELECT ip.invoice_id AS invoiceId,ip.order_no AS orderNo,ip.product_id AS productId,o.BranchNo AS branchNo,b.BranchName AS branchName,ip.original_amount AS originalAmount,ip.billed_amount AS amount,ip.source_invoice_id AS sourceInvoiceId,s.invoice_no AS sourceInvoiceNo FROM invoice_products ip JOIN orders o ON o.OrderNo=ip.order_no JOIN branches b ON b.BranchNo=o.BranchNo LEFT JOIN invoices s ON s.id=ip.source_invoice_id WHERE ip.invoice_id IN ("+marks+") ORDER BY ip.invoice_id,ip.order_no,ip.product_id LIMIT 10001",batch.toArray());
   if(lines.size()>10000)throw fail("Забагато виробів. Скоротіть період звірки");
   var oldLines=db.queryForList("SELECT io.invoice_id AS invoiceId,io.order_no AS orderNo,o.BranchNo AS branchNo,b.BranchName AS branchName,io.amount FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE io.invoice_id IN ("+marks+") AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id) ORDER BY io.invoice_id,io.order_no LIMIT 10001",batch.toArray());
   if(oldLines.size()>10000)throw fail("Забагато замовлень. Скоротіть період звірки");
   var balances=new HashMap<Long,BigDecimal>();for(var r:ledger.invoiceBalances(partner,until,batch))balances.put(((Number)r[0]).longValue(),(BigDecimal)r[1]);
   for(var h:headers){long id=number(h,"id");var detail=new ArrayList<Map<String,Object>>();for(var l:lines)if(number(l,"invoiceId")==id)detail.add(l);for(var l:oldLines)if(number(l,"invoiceId")==id)detail.add(l);h.put("lines",detail);h.put("ledgerBalanceAtEnd",balances.getOrDefault(id,BigDecimal.ZERO));invoices.add(h);byId.put(id,h);}
  }
  var movements=new ArrayList<Map<String,Object>>();BigDecimal running=totals.get("opening");
  for(var e:entries){running=running.add(ReconciliationMath.delta(e.direction().name(),e.amount()));var m=new LinkedHashMap<String,Object>();m.put("id",e.id());m.put("date",e.createdAt().atZoneSameInstant(ReconciliationMath.ZONE).toLocalDateTime().toString());m.put("branchName",e.branchName());m.put("category",e.category().name());m.put("label",ReconciliationMath.label(e.category().name()));m.put("direction",e.direction().name());m.put("amount",e.amount());m.put("balance",running);m.put("invoiceId",e.invoiceId());m.put("invoiceNo",e.invoiceId()!=null&&byId.containsKey(e.invoiceId())?byId.get(e.invoiceId()).get("invoiceNo"):null);m.put("reference",e.reference());m.put("note",e.note());movements.add(m);}
  var warnings=new ArrayList<String>();
  warnings.add("Сальдо: додатне — борг фірми, від’ємне — кошти фірми на балансі підприємства. Внутрішні перенесення не є новою оплатою.");
  warnings.add("Звірка охоплює проводки, де боржником є ця фірма. Платежі за іншого боржника сюди не включаються. Відсутні старі проводки автоматично не відновлюються.");
  warnings.add("Доступний аванс зараз наведено окремо з чинного механізму оплат. Це не аванс на історичну кінцеву дату.");
  if(invoices.stream().anyMatch(i->i.get("commercialOwner")==null))warnings.add("Є рахунки без збереженої ознаки посередника: показано їх фактичні проводки. Реселлерський прайс заднім числом не застосовується; старі дані потребують перевірки.");
  if(byId.size()!=ids.size())warnings.add("Для частини проводок не знайдено рахунок. Проводки включено в баланс без втрати сум.");
  var result=new LinkedHashMap<String,Object>();result.put("partnerId",partner);result.put("partnerName",firms.get(0).get("name"));result.put("from",from.toString());result.put("to",to.toString());result.put("generatedAt",ZonedDateTime.now(ReconciliationMath.ZONE).toOffsetDateTime().toString());result.put("totals",totals);result.put("unallocatedLedgerAtEnd",ledger.unallocated(partner,until));result.put("availableAdvanceNow",transactions.calculateAvailableAdvance(partner));result.put("entries",movements);result.put("invoices",invoices);result.put("warnings",warnings);return result;
 }
 private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private static PreviewException fail(String message){return new PreviewException(HttpStatus.BAD_REQUEST,"RECONCILIATION_INPUT",message);}
}
