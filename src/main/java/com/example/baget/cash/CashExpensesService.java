package com.example.baget.cash;

import com.example.baget.users.User;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CashExpensesService {
 private static final Set<String> CATEGORIES=Set.of("RENT","UTILITIES","REPAIR","HOUSEHOLD","OTHER");
 private final JdbcTemplate db;
 private final CashService cash;
 public CashExpensesService(JdbcTemplate db,CashService cash){this.db=db;this.cash=cash;}
 private User editor(Authentication a){var u=cash.user(a);if(!CashService.editor(u))throw CashService.denied();return u;}
 private LocalDate today(){return LocalDate.now(CashService.CASH_ZONE);}
 private void spendable(Map<String,Object> account){cash.ready(account);if(!Set.of("BRANCH_CASH","CENTRAL_CASH","BANK").contains(account.get("kind")))throw CashService.bad("Оберіть касу або банківський рахунок підприємства");}
 private static String text(String value,int max,String field,boolean required){String v=value==null?"":value.trim();if(v.length()>max||(required&&v.isEmpty()))throw CashService.bad("Перевірте поле: "+field);return v;}
 private void category(String value){if(value==null||!CATEGORIES.contains(value))throw CashService.bad("Невідома категорія поточних витрат");}
 private List<Map<String,Object>> accounts(User u){return db.queryForList("SELECT a.id,a.name,a.kind,a.branch_no AS branchNo,a.opened_at AS openedAt FROM cash_accounts a JOIN branch_commercial_settings s ON s.branch_no=a.branch_no AND s.mode='OWN' WHERE a.initialized=1 AND a.kind IN ('BRANCH_CASH','CENTRAL_CASH','BANK') ORDER BY a.name,a.id").stream().filter(r->!"BRANCH_CASH".equals(r.get("kind"))||CashService.role(u,"ROLE_ADMIN")||u.getAllowedBranches().stream().anyMatch(b->b.getBranchNo()==CashService.num(r,"branchNo"))).toList();}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object options(Authentication a){return Map.of("accounts",accounts(editor(a)),"today",today());}
 private String scope(User u,List<Object> args){
  if(CashService.role(u,"ROLE_ADMIN"))return "";
  var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).toList();
  if(ids.isEmpty())return " AND a.kind<>'BRANCH_CASH'";
  args.addAll(ids);return " AND (a.kind<>'BRANCH_CASH' OR a.branch_no IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+"))";
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Object list(LocalDate from,LocalDate to,Long accountId,String category,int page,Authentication a){
  var u=editor(a);if(from==null||to==null||from.isAfter(to)||page<0||page>100000)throw CashService.bad("Перевірте період і сторінку");
  var args=new ArrayList<Object>();args.add(from);args.add(to);
  String where=" WHERE e.business_day>=? AND e.business_day<=?"+scope(u,args);
  if(accountId!=null){if(accountId<=0)throw CashService.bad("Некоректний рахунок");where+=" AND e.cash_account_id=?";args.add(accountId);}
  if(category!=null&&!category.isBlank()){category(category);where+=" AND e.category=?";args.add(category);}
  String joins=" FROM cash_expenses e JOIN cash_accounts a ON a.id=e.cash_account_id JOIN ledger_entries l ON l.cash_expense_id=e.id";
  var totals=db.queryForList("SELECT COALESCE(SUM(CASE WHEN l.direction='OUT' THEN l.amount ELSE 0 END),0) AS spent,COALESCE(SUM(CASE WHEN l.direction='IN' THEN l.amount ELSE 0 END),0) AS reversed,COALESCE(SUM(CASE WHEN l.direction='OUT' THEN l.amount ELSE -l.amount END),0) AS net"+joins+where,args.toArray());
  args.add(page*50);
  var rows=db.queryForList("SELECT e.id,e.cash_account_id AS accountId,a.name AS accountName,e.business_day AS day,e.category,e.kind,e.payee,e.document_ref AS documentRef,e.note,e.actor,e.created_at AS createdAt,e.reversal_of AS reversalOf,l.amount,l.direction,(e.kind='EXPENSE' AND NOT EXISTS(SELECT 1 FROM cash_expenses x WHERE x.reversal_of=e.id)) AS canReverse"+joins+where+" ORDER BY e.id DESC LIMIT 51 OFFSET ?",args.toArray());
  return Map.of("rows",rows.subList(0,Math.min(50,rows.size())),"hasMore",rows.size()>50,"totals",totals.get(0));
 }
 public record Spend(@NotBlank String requestKey,@Positive long accountId,@NotNull LocalDate day,
  @NotBlank String category,@NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal amount,
  @NotBlank @Size(max=255) String payee,@Size(max=120) String documentRef,@NotBlank @Size(max=1000) String note){}
 public Object spend(Spend r,Authentication a){
  var u=editor(a);cash.serialize();var acc=cash.account(r.accountId());cash.view(u,acc);
  var prior=cash.prior("EXPENSE",r.requestKey(),r,u);if(prior!=null)return prior;
  spendable(acc);CashService.positive(r.amount());category(r.category());
  if(r.day()==null||r.day().isAfter(today()))throw CashService.bad("Дата витрати не може бути в майбутньому");
  Object opened=acc.get("opened_at");if(opened==null)throw CashService.bad("Не визначено початок обліку каси");
  LocalDate openingDay=opened instanceof java.sql.Timestamp t?t.toLocalDateTime().toLocalDate():LocalDate.parse(opened.toString().substring(0,10));
  if(r.day().isBefore(openingDay))throw CashService.bad("Витрата передує початковому залишку. Не реєструйте повторно платежі, враховані в ньому");
  String payee=text(r.payee(),255,"Отримувач",true),doc=text(r.documentRef(),120,"Документ",false),note=text(r.note(),1000,"Призначення",true);
  cash.enough(r.accountId(),r.amount());
  db.update("INSERT INTO cash_expenses(cash_account_id,business_day,category,kind,payee,document_ref,note,actor) VALUES(?,?,?,'EXPENSE',?,?,?,?)",r.accountId(),r.day(),r.category(),payee,doc,note,u.getUsername());
  long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);post(acc,u,id,"OUT",r.amount(),payee+" · "+note);
  return cash.remember("EXPENSE",r.requestKey(),r,Map.of("id",id,"message","Витрату проведено. Залишок каси / рахунку зменшено"),u);
 }
 public record Reverse(@NotBlank String requestKey,@NotBlank @Size(max=1000) String note){}
 public Object reverse(long id,Reverse r,Authentication a){
  var u=editor(a);cash.serialize();
  var rows=db.queryForList("SELECT e.*,l.amount,l.direction FROM cash_expenses e JOIN ledger_entries l ON l.cash_expense_id=e.id WHERE e.id=?",id);
  if(rows.isEmpty())throw CashService.bad("Витрату не знайдено");var original=rows.get(0);
  var acc=cash.account(CashService.num(original,"cash_account_id"));cash.view(u,acc);
  var payload=Map.of("expenseId",id,"reversal",r);var prior=cash.prior("EXPENSE_REVERSAL",r.requestKey(),payload,u);if(prior!=null)return prior;
  spendable(acc);if(!"EXPENSE".equals(original.get("kind"))||!"OUT".equals(original.get("direction")))throw CashService.bad("Сторнувати можна лише початкову витрату");
  if(!db.queryForList("SELECT id FROM cash_expenses WHERE reversal_of=?",id).isEmpty())throw CashService.bad("Витрату вже сторновано");
  String reason=text(r.note(),1000,"Причина сторнування",true);var amount=CashService.decimal(original,"amount");CashService.positive(amount);
  db.update("INSERT INTO cash_expenses(cash_account_id,business_day,category,kind,payee,document_ref,note,actor,reversal_of) VALUES(?,?,?,'REVERSAL',?,?,?,?,?)",acc.get("id"),today(),original.get("category"),original.get("payee"),original.get("document_ref"),reason,u.getUsername(),id);
  long reversal=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);post(acc,u,reversal,"IN",amount,"Сторно витрати №"+id+": "+reason);
  return cash.remember("EXPENSE_REVERSAL",r.requestKey(),payload,Map.of("id",reversal,"message","Помилкову витрату сторновано в початковій касі / на рахунку"),u);
 }
 private void post(Map<String,Object> acc,User u,long id,String direction,BigDecimal amount,String note){
  db.update("INSERT INTO ledger_entries(branch_id,direction,category,amount,created_at,created_by,cash_account_id,cash_expense_id,reference,note) VALUES(?,?,'OVERHEAD_EXPENSE',?,CURRENT_TIMESTAMP(6),?,?,?,?,?)",acc.get("branch_no"),direction,amount,u.getId(),acc.get("id"),id,"EXPENSE-"+id,note);
 }
}
