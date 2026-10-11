package com.example.baget.cash;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CashDayEndService {
 private final JdbcTemplate db;private final CashService cash;
 public CashDayEndService(JdbcTemplate db,CashService cash){this.db=db;this.cash=cash;}
 public Object read(LocalDate day,Authentication auth){
  var u=cash.user(auth);if(!CashService.editor(u))throw CashService.denied();
  var today=LocalDate.now(CashService.CASH_ZONE);if(day==null)day=today;if(day.isAfter(today))throw CashService.bad("Не можна переглянути майбутній день");
  var args=new ArrayList<Object>();args.add(Timestamp.from(day.plusDays(1).atStartOfDay(CashService.CASH_ZONE).toInstant()));args.add(day);
  String scope="";
  if(!CashService.role(u,"ROLE_ADMIN")){var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();scope=ids.isEmpty()?" AND a.kind='CENTRAL_CASH'":" AND (a.kind='CENTRAL_CASH' OR a.branch_no IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+"))";args.addAll(ids);}
  var source=db.queryForList("""
   SELECT a.id,a.name,a.kind,a.initialized,b.BranchName AS branchName,
    COALESCE(l.revision,0) AS revision,COALESCE(l.expected,0) AS expected,
    r.id AS confirmationId,r.last_ledger_id AS lastLedgerId,r.expected_amount AS confirmedExpected,
    r.actual_amount AS actual,r.difference,r.actor,r.note,r.created_at AS confirmedAt
   FROM cash_accounts a JOIN branch_commercial_settings s ON s.branch_no=a.branch_no AND s.mode='OWN'
   LEFT JOIN branches b ON b.BranchNo=a.branch_no
   LEFT JOIN (SELECT cash_account_id,MAX(id) AS revision,SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END) AS expected
    FROM ledger_entries WHERE created_at<? GROUP BY cash_account_id) l ON l.cash_account_id=a.id
   LEFT JOIN cash_reconciliations r ON r.id=(SELECT MAX(x.id) FROM cash_reconciliations x WHERE x.account_id=a.id AND x.business_day=?)
   WHERE a.kind IN ('BRANCH_CASH','CENTRAL_CASH')
   """+scope+" ORDER BY a.name,a.id",args.toArray());
  var rows=new ArrayList<Map<String,Object>>();
  for(var original:source){var row=new LinkedHashMap<>(original);Map<String,Object> latest=null;
   if(row.get("confirmationId")!=null)latest=Map.of("lastLedgerId",row.get("lastLedgerId"),"expected",row.get("confirmedExpected"),"difference",row.get("difference"));
   row.put("status",CashService.yes(row.get("initialized"))?CashDayMath.confirmation(CashService.num(row,"revision"),CashService.decimal(row,"expected"),latest):"NOT_INITIALIZED");rows.add(row);
  }
  return Map.of("day",day,"today",today,"rows",rows);
 }
}
