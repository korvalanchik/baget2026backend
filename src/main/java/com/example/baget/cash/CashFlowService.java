package com.example.baget.cash;

import com.example.baget.users.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CashFlowService {
 private final JdbcTemplate db;private final CashService cash;
 public CashFlowService(JdbcTemplate db,CashService cash){this.db=db;this.cash=cash;}
 private String scope(User u,List<Object> args){
  if(CashService.role(u,"ROLE_ADMIN"))return "";
  var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();
  if(ids.isEmpty())return " AND a.kind<>'BRANCH_CASH'";
  args.addAll(ids);return " AND (a.kind<>'BRANCH_CASH' OR a.branch_no IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+"))";
 }
 public Object report(LocalDate from,LocalDate to,Authentication auth){
  var u=cash.user(auth);if(!CashService.editor(u))throw CashService.denied();
  LocalDate today=LocalDate.now(CashService.CASH_ZONE);
  if(from==null)from=today.withDayOfMonth(1);if(to==null)to=today;
  if(from.isAfter(to)||to.isAfter(today)||ChronoUnit.DAYS.between(from,to)>365)throw CashService.bad("Оберіть період до 366 днів, не пізніше сьогодні");
  var accountArgs=new ArrayList<Object>();String accountScope=scope(u,accountArgs);
  var accounts=db.queryForList("SELECT a.id,a.name,a.kind,a.initialized,a.opened_at AS openedAt,b.BranchName AS branchName FROM cash_accounts a LEFT JOIN branches b ON b.BranchNo=a.branch_no WHERE 1=1"+accountScope+" ORDER BY a.id",accountArgs.toArray());
  var start=Timestamp.from(from.atStartOfDay(CashService.CASH_ZONE).toInstant());
  var end=Timestamp.from(to.plusDays(1).atStartOfDay(CashService.CASH_ZONE).toInstant());
  var args=new ArrayList<Object>();args.add(start);args.add(end);String filter=scope(u,args);
  var groups=db.queryForList("""
   SELECT l.cash_account_id AS accountId,CASE WHEN l.created_at<? THEN 'BEFORE' ELSE 'PERIOD' END AS period,
   l.category,l.direction,
   CASE WHEN l.category='CASH_OPENING' THEN 'INITIAL'
        WHEN l.category='CASH_TRANSFER' THEN 'TRANSFER'
        WHEN e.kind='REVERSAL' OR rp.kind='REVERSAL' OR (l.category='MANAGER_REWARD' AND l.direction='IN') THEN 'REVERSAL'
        ELSE 'EXTERNAL' END AS bucket,SUM(l.amount) AS amount
   FROM ledger_entries l JOIN cash_accounts a ON a.id=l.cash_account_id
   LEFT JOIN cash_expenses e ON e.id=l.cash_expense_id
   LEFT JOIN receipt_payments rp ON rp.id=l.receipt_payment_id
   WHERE l.created_at<?
   """+filter+" GROUP BY l.cash_account_id,period,l.category,l.direction,bucket ORDER BY l.cash_account_id,period,l.category,l.direction,bucket",args.toArray());
  var byAccount=new HashMap<Long,List<Map<String,Object>>>();
  for(var row:groups)byAccount.computeIfAbsent(CashService.num(row,"accountId"),id->new ArrayList<>()).add(row);
  var rows=new ArrayList<Map<String,Object>>();var totals=new ArrayList<CashFlowMath.Totals>();
  try{
   for(var acc:accounts){var movements=byAccount.getOrDefault(CashService.num(acc,"id"),List.of());var total=CashFlowMath.summarize(movements);totals.add(total);var row=new LinkedHashMap<>(acc);row.put("totals",total);row.put("movements",movements.stream().filter(m->"PERIOD".equals(m.get("period"))).toList());rows.add(row);}
  }catch(IllegalArgumentException e){throw CashService.bad(e.getMessage());}
  long allAccounts=db.queryForObject("SELECT COUNT(*) FROM cash_accounts",Long.class);
  var combined=CashFlowMath.combine(totals);
  return Map.of("generatedAt",java.time.OffsetDateTime.now(CashService.CASH_ZONE),"from",from,"to",to,"today",today,"rows",rows,"totals",combined,
   "completeScope",allAccounts==accounts.size(),"uninitializedCount",accounts.stream().filter(a->!CashService.yes(a.get("initialized"))).count(),
   "transferDifference",combined.transferIn().subtract(combined.transferOut()));
 }
}
