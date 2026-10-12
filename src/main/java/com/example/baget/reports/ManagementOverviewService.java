package com.example.baget.reports;

import com.example.baget.users.UsersRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ManagementOverviewService {
 private final JdbcTemplate db;private final UsersRepository users;private final OwnerResultsService owner;
 public ManagementOverviewService(JdbcTemplate db,UsersRepository users,OwnerResultsService owner){this.db=db;this.users=users;this.owner=owner;}
 private void access(Authentication a){if(a==null || !a.isAuthenticated() || !users.findByUsername(a.getName()).map(u->u.isEnabled()&&u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()))).orElse(false))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Огляд доступний адміністратору");}
 public Object branches(Authentication a){access(a);return db.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName");}
 static final ZoneId ZONE=ZoneId.of("Europe/Kyiv");
 static void validate(LocalDate from,LocalDate to,Long branch,String mode,LocalDate today){
  if(from==null || to==null || to.isBefore(from) || to.isAfter(today) || ChronoUnit.DAYS.between(from,to)>365 || branch!=null&&branch<=0 || mode==null || !Set.of("ALL","OWN","RESELLER").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Вкажіть період до 366 днів, не пізніше сьогодні, та коректну філію");
 }
 static final String OPEN="p.production_state IN ('WAITING','ACCEPTED','IN_PROGRESS','PAUSED') AND p.current_location<>'CUSTOMER'";
 static final String PRICE="EXISTS(SELECT 1 FROM product_price_approval a WHERE a.product_id=p.id AND a.state IN ('WAITING','REJECTED'))";
 static final String SHORTAGE="EXISTS(SELECT 1 FROM material_reservations r WHERE r.order_product_id=p.id AND r.active=1 AND r.demand>r.reserved)";
 static final String BASE=" FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo ";
 private static String filter(Long branch,String mode,List<Object> args){String s="";if(branch!=null){s+=" AND o.BranchNo=?";args.add(branch);}if(!mode.equals("ALL")){s+=" AND o.commerce_mode=?";args.add(mode);}return s;}
 public Map<String,Object> read(LocalDate from,LocalDate to,Long branch,String mode,Authentication a){
  access(a);LocalDate today=LocalDate.now(ZONE);validate(from,to,branch,mode,today);
  var start=Timestamp.from(from.atStartOfDay(ZONE).toInstant());var end=Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
  var period=new LinkedHashMap<String,Object>();var branchRows=new ArrayList<Map<String,Object>>();
  var sources=List.of(
   new Source("orders","SELECT o.BranchNo AS branchNo,b.BranchName AS branchName,COALESCE(o.commerce_mode,'UNSET') AS commerceMode,COUNT(*) AS quantity FROM orders o JOIN branches b ON b.BranchNo=o.BranchNo WHERE o.SaleDate>=? AND o.SaleDate<? AND EXISTS(SELECT 1 FROM order_products p WHERE p.order_no=o.OrderNo)"),
   new Source("completed","SELECT o.BranchNo AS branchNo,b.BranchName AS branchName,COALESCE(o.commerce_mode,'UNSET') AS commerceMode,COUNT(*) AS quantity FROM (SELECT order_product_id AS product_id,MIN(created_at) AS firstAt FROM product_production_events WHERE action='COMPLETE' GROUP BY order_product_id) e JOIN order_products p ON p.id=e.product_id JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE e.firstAt>=? AND e.firstAt<?"),
   new Source("issued","SELECT o.BranchNo AS branchNo,b.BranchName AS branchName,COALESCE(o.commerce_mode,'UNSET') AS commerceMode,COUNT(*) AS quantity FROM (SELECT product_id,MIN(created_at) AS firstAt FROM product_transfers WHERE action='ACCEPT' AND to_location='CUSTOMER' GROUP BY product_id) e JOIN order_products p ON p.id=e.product_id JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo WHERE e.firstAt>=? AND e.firstAt<?")
  );
  for(var source:sources){var args=new ArrayList<Object>();args.add(start);args.add(end);var rows=db.queryForList(source.sql()+filter(branch,mode,args)+" GROUP BY o.BranchNo,b.BranchName,o.commerce_mode ORDER BY b.BranchName,o.BranchNo",args.toArray());long total=0;for(var r:rows){total+=((Number)r.get("quantity")).longValue();var item=new LinkedHashMap<String,Object>(r);item.put("kind",source.key());branchRows.add(item);}period.put(source.key(),total);}
  var args=new ArrayList<Object>();args.add(java.sql.Date.valueOf(today));String scope=filter(branch,mode,args);
  String operational="SELECT COUNT(*) AS unfinished,COALESCE(SUM(p.production_state='WAITING'),0) AS waiting,COALESCE(SUM(p.production_state='IN_PROGRESS'),0) AS inProgress,COALESCE(SUM(DATE(o.ShipDate)<?),0) AS late,COALESCE(SUM("+PRICE+"),0) AS approval,COALESCE(SUM("+SHORTAGE+"),0) AS shortage"+BASE+" WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND ("+OPEN+")"+scope;
  var current=new LinkedHashMap<String,Object>(db.queryForMap(operational,args.toArray()));
  args=new ArrayList<>();scope=filter(branch,mode,args);
  current.put("readyPickup",db.queryForObject("SELECT COUNT(*)"+BASE+" WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.current_location='BRANCH' AND p.pending_location IS NULL AND p.active_rework_id IS NULL"+scope,Long.class,args.toArray()));
  current.put("awaitingBranch",db.queryForObject("SELECT COUNT(*)"+BASE+" WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND p.production_state='COMPLETED' AND p.pending_location='BRANCH'"+scope,Long.class,args.toArray()));
  args=new ArrayList<>();args.add(java.sql.Date.valueOf(today));args.add(java.sql.Date.valueOf(today));scope=filter(branch,mode,args);
  var attention=db.queryForList("SELECT p.id AS productId,p.order_no AS orderNo,p.client_product_id AS productName,b.BranchName AS branchName,o.ShipDate AS dueDate,p.production_state AS state,DATE(o.ShipDate)<? AS late,"+PRICE+" AS approval,"+SHORTAGE+" AS shortage"+BASE+" WHERE p.active=1 AND o.StatusOrder NOT IN (5,10) AND ("+OPEN+") AND (DATE(o.ShipDate)<? OR "+PRICE+" OR "+SHORTAGE+")"+scope+" ORDER BY o.ShipDate IS NULL,o.ShipDate,p.id LIMIT 21",args.toArray());
  var result=new LinkedHashMap<String,Object>();result.put("from",from);result.put("to",to);result.put("branch",branch);result.put("mode",mode);result.put("asOf",OffsetDateTime.now(ZONE));result.put("period",period);result.put("branches",branchRows);result.put("current",current);result.put("attention",attention.subList(0,Math.min(20,attention.size())));result.put("moreAttention",attention.size()>20);
  // Reuse the existing validated sales/material-cost/reward calculation; never use orders financial columns.
  Object financial=null;String financialError=null;
  if(!mode.equals("RESELLER"))try{financial=((Map<?,?>)owner.report(from,to,branch,0,a)).get("summary");}catch(com.example.baget.calculation.preview.PreviewException e){financialError=e.getMessage();}
  result.put("ownResults",financial);result.put("financialError",financialError);return result;
 }
 private record Source(String key,String sql){}
}
