package com.example.baget.reports;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api/reports/export")
public class ReportExportController {
 private final OwnerResultsService own;private final PartnerResultsController partner;private final CashReceiptsController cash;private final JdbcTemplate db;
 public ReportExportController(OwnerResultsService own,PartnerResultsController partner,CashReceiptsController cash,JdbcTemplate db){this.own=own;this.partner=partner;this.cash=cash;this.db=db;}
 @SuppressWarnings("unchecked")
 @GetMapping("/{kind}") public ResponseEntity<?> export(@PathVariable("kind") String kind,@RequestParam("from") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam("to") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(name="branch",required=false) Long branch,@RequestParam(name="partner",required=false) Long partnerId,@RequestParam(name="format",defaultValue="json") String format,Authentication a){
  if(!Set.of("json","xlsx").contains(format))throw new PreviewException(HttpStatus.BAD_REQUEST,"FORMAT","Невідомий формат");
  Map<String,Object> result=(Map<String,Object>)switch(kind){case "own"->own.report(from,to,branch,-1,a);case "partner"->partner.report(from,to,partnerId,branch,-1,a);case "cash"->cash.report(from,to,branch,-1,a);default->throw new PreviewException(HttpStatus.BAD_REQUEST,"REPORT","Невідомий звіт");};
  var summary=(Map<String,Object>)result.get("summary");var source=(List<Map<String,Object>>)result.get("rows");var notes=new ArrayList<String>();notes.add("Період: "+from+" — "+to+" (Europe/Kyiv)");
  String branchName=branch==null?"Усі":db.queryForList("SELECT BranchName FROM branches WHERE BranchNo=?",String.class,branch).stream().findFirst().orElse("№ "+branch);notes.add("Філія: "+branchName);
  notes.add("Сформовано: "+OffsetDateTime.now(ZoneId.of("Europe/Kyiv")));notes.add("Усі рядки за фільтрами. Суми у гривнях. Це знімок даних, не розрахункова модель.");
  var sums=new ArrayList<List<Object>>();List<String> keys;List<String> headers;String title;
  if("cash".equals(kind)){title="Рух грошей";keys=List.of("id","createdAt","kind","customerName","payerName","branchName","invoiceNo","signedAmount","note");headers=List.of("Проводка","Дата","Операція","Клієнт","Платник","Філія","Рахунок","Сума, грн","Примітка");
   add(sums,"Аванси",summary.get("advance"));add(sums,"Оплати",summary.get("payment"));add(sums,"Надійшло разом",summary.get("received"));add(sums,"Повернення",summary.get("refund"));add(sums,"Чисті надходження",summary.get("net"));add(sums,"Проводок",summary.get("count"));notes.add("Без внутрішніх зарахувань та об'єднань. Не є залишком каси; інші виплати не включено.");
  }else{title="partner".equals(kind)?"Результати роботи з посередниками":"Результати власних філій";
   keys="partner".equals(kind)?List.of("invoiceNo","invoicedAt","partnerName","branchName","orderNo","productName","saleAmount","materialAmount","unknownMaterials","difference"):List.of("issuedAt","branchName","orderNo","productName","saleAmount","materialAmount","unknownMaterials","rewardAmount","rewardState","difference");
   headers="partner".equals(kind)?List.of("Первісний рахунок","Дата рахунку","Фірма","Філія","Замовлення","Виріб","Виставлено","Матеріали (відома частина)","Неоцінених позицій","Різниця"):List.of("Дата видачі","Філія","Замовлення","Виріб","Вартість видачі","Матеріали (відома частина)","Неоцінених позицій","Винагорода","Стан винагороди","Різниця");
   add(sums,"Карток / позицій виробів",summary.get("productCount"));add(sums,"Відома вартість",summary.get("knownSales"));add(sums,"Матеріали: відома частина",summary.get("knownMaterials"));if("own".equals(kind))add(sums,"Нарахована винагорода",summary.get("rewards"));add(sums,"Неповних карток",summary.get("incompleteCount"));add(sums,"Різниця",summary.get("difference"));add(sums,"Різниця тільки оцінених карток",summary.get("completeDifference"));notes.add("Різниця не є чистим прибутком. Невідомі суми позначені «Не визначено»; матеріали можуть бути оцінені частково.");notes.add("Оборот за первісними рахунками. Перенесення залишку боргу не є повторним продажем.");
   if("partner".equals(kind)){notes.add("Фірма: "+(partnerId==null?"Усі":db.queryForList("SELECT Company FROM customer WHERE CustNo=?",String.class,partnerId).stream().findFirst().orElse("№ "+partnerId)));add(sums,"Первісних рахунків",summary.get("invoiceCount"));add(sums,"Поточний борг (без обмеження періодом/філією)",result.get("debtTotal"));for(var debt:(List<Map<String,Object>>)result.get("debts"))add(sums,"Борг: "+debt.get("partnerName"),debt.get("debt"));notes.add("Борг за чинними рахунками на момент формування; незараховані аванси не віднято.");}else notes.add("Перша видача клієнту. Винагорода включає поточні коригування. Повернення не моделюються.");
  }
  var rows=new ArrayList<List<Object>>();for(var item:source){var row=new ArrayList<Object>();for(String key:keys){Object v=item.get(key);if(Set.of("id","orderNo","invoiceNo").contains(key)&&v!=null)v=v.toString();if("kind".equals(key))v=switch(Objects.toString(v,"")){case "ADVANCE"->"Аванс";case "PAYMENT"->"Оплата";case "REFUND"->"Повернення";default->v;};if("rewardState".equals(key))v="REVIEW".equals(v)?"Потребує уточнення":"POSTED".equals(v)?"Нараховано":"Немає нарахування";row.add(v==null?(Set.of("saleAmount","difference").contains(key)?"Не визначено":""):v);}rows.add(row);}
  var table=new ReportWorkbook.Table(title,notes,sums,headers,rows);
  if("xlsx".equals(format))return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"report-"+kind+"-"+from+".xlsx\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(ReportWorkbook.write(table));
  return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(table);
 }
 private static void add(List<List<Object>> rows,String label,Object value){rows.add(List.of(label,value==null?"Не визначено":value));}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage()));}
}
