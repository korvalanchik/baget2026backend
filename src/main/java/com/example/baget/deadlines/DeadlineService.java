package com.example.baget.deadlines;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class DeadlineService {
 private final JdbcTemplate db;private final LeadTimeService settings;
 public DeadlineService(JdbcTemplate db,LeadTimeService settings){this.db=db;this.settings=settings;}
 private static final ZoneId ZONE=ZoneId.of("Europe/Kyiv");
 private static final String APPROVAL="EXISTS(SELECT 1 FROM product_price_approval x WHERE x.product_id=p.id AND x.state IN ('WAITING','REJECTED'))";
 private static final String SHORTAGE="EXISTS(SELECT 1 FROM material_reservations r WHERE r.order_product_id=p.id AND r.active=1 AND r.demand>r.reserved)";
 private static final String OPEN="p.production_state NOT IN ('COMPLETED','STOPPED') AND p.current_location<>'CUSTOMER'";
 private static final String PICKUP="p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.current_location='BRANCH' AND p.pending_location IS NULL AND p.active_rework_id IS NULL";
 private static final String FROM=" FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN customer c ON c.CustNo=o.CustNo JOIN branches b ON b.BranchNo=o.BranchNo WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND ";
 private String condition(String bucket){return switch(bucket){case "soon"->OPEN+" AND DATE(o.ShipDate)>=? AND DATE(o.ShipDate)<=?";case "attention"->OPEN+" AND (DATE(o.ShipDate)<? OR "+APPROVAL+" OR "+SHORTAGE+")";case "pickup"->PICKUP;default->throw LeadTimeService.bad("Невідомий список завдань");};}
 private List<Object> args(String bucket,LocalDate today,LocalDate horizon){var args=new ArrayList<Object>();if(!bucket.equals("pickup"))args.add(today);if(bucket.equals("soon"))args.add(horizon);return args;}
 public Object read(String bucket,int page,Authentication a){var u=settings.user(a);if(!LeadTimeService.role(u,"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER","ROLE_FOREMAN"))throw LeadTimeService.denied();if(page<0||page>100000)throw LeadTimeService.bad("Некоректна сторінка");condition(bucket);
  var today=LocalDate.now(ZONE);var cfg=settings.current();var horizon=LeadTimeService.addWorkingDays(today,cfg.upcomingDays());
  String scope="";var branches=new ArrayList<Object>();if(!LeadTimeService.role(u,"ROLE_ADMIN")){branches.addAll(u.getAllowedBranches().stream().map(b->b.getBranchNo()).distinct().sorted().toList());scope=branches.isEmpty()?" AND 1=0":" AND o.BranchNo IN ("+String.join(",",Collections.nCopies(branches.size(),"?"))+")";}
  var counts=new LinkedHashMap<String,Long>();for(String key:List.of("soon","attention","pickup")){var values=args(key,today,horizon);values.addAll(branches);counts.put(key,db.queryForObject("SELECT COUNT(*)"+FROM+condition(key)+scope,Long.class,values.toArray()));}
  var values=args(bucket,today,horizon);values.addAll(branches);values.add(page*20);
  var rows=db.queryForList("SELECT p.id AS productId,p.client_product_id AS productName,o.OrderNo AS orderNo,o.CustNo AS customerId,c.Company AS customerName,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS phone,b.BranchName AS branchName,o.BranchNo AS branchNo,DATE(o.ShipDate) AS dueDate,p.production_state AS state,p.current_location AS location,"+APPROVAL+" AS needsApproval,"+SHORTAGE+" AS shortage,(SELECT MAX(t.created_at) FROM product_transfers t WHERE t.product_id=p.id AND t.action='ACCEPT' AND t.to_location='BRANCH') AS branchReceivedAt"+FROM+condition(bucket)+scope+" ORDER BY "+(bucket.equals("pickup")?"branchReceivedAt IS NULL,branchReceivedAt":"o.ShipDate IS NULL,o.ShipDate")+",o.OrderNo,p.id LIMIT 21 OFFSET ?",values.toArray());
  var result=new ArrayList<Map<String,Object>>();for(var source:rows.subList(0,Math.min(20,rows.size()))){var row=new LinkedHashMap<>(source);var received=row.get("branchReceivedAt");row.put("waitingDays",received==null?null:Math.max(0,ChronoUnit.DAYS.between(receivedDay(received),today)));result.add(row);}
  return Map.of("rows",result,"counts",counts,"hasMore",rows.size()>20,"page",page,"today",today,"horizon",horizon,"upcomingDays",cfg.upcomingDays());
 }
 static LocalDate receivedDay(Object v){if(v instanceof java.sql.Timestamp t)return t.toInstant().atZone(ZONE).toLocalDate();if(v instanceof LocalDateTime d)return d.toLocalDate();if(v instanceof LocalDate d)return d;return LocalDate.parse(v.toString().substring(0,10));}
}
