package com.example.baget.tasks;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Read-only queues. All mutations still go through the existing domain services. */
@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class TaskService {
 private final JdbcTemplate db;
 private final UsersRepository users;
 public TaskService(JdbcTemplate db, UsersRepository users){this.db=db;this.users=users;}
 record Task(String key,String group,String title,String unit,String target,String from,String columns,String condition,String ordering){}
 private static final String PRODUCT_FROM=" FROM order_products p JOIN orders o ON o.OrderNo=p.order_no JOIN branches b ON b.BranchNo=o.BranchNo JOIN customer c ON c.CustNo=o.CustNo ";
 private static final String PRODUCT_COLUMNS="p.id AS productId,o.OrderNo AS orderNo,p.client_product_id AS productName,c.Company AS customerName,b.BranchName AS branchName,o.BranchNo AS branchNo,o.ShipDate AS dueDate";
 private static final String ACTIVE="p.active=1 AND o.StatusOrder NOT IN (5,10)";
 private static final String APPROVAL="EXISTS(SELECT 1 FROM product_price_approval x WHERE x.product_id=p.id AND x.state IN ('WAITING','REJECTED'))";
 private static final String OPEN="p.production_state NOT IN ('COMPLETED','STOPPED') AND p.current_location<>'CUSTOMER'";
 private static Task product(String key,String title,String condition){return new Task(key,"workshop",title,"виробів","workshop",PRODUCT_FROM,PRODUCT_COLUMNS,ACTIVE+" AND "+condition,"o.OrderNo,p.id");}
 private static Task branch(String key,String title,String condition){return new Task(key,"manager",title,"замовлень","orders"," FROM orders o JOIN branches b ON b.BranchNo=o.BranchNo JOIN customer c ON c.CustNo=o.CustNo ","o.OrderNo AS orderNo,c.Company AS customerName,b.BranchName AS branchName,o.BranchNo AS branchNo,o.ShipDate AS dueDate","o.StatusOrder NOT IN (5,10) AND EXISTS(SELECT 1 FROM order_products p WHERE p.order_no=o.OrderNo AND p.active=1 AND p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.active_rework_id IS NULL AND "+condition+")","o.OrderNo");}
 static final List<Task> TASKS=List.of(
  branch("receive","Прийняти на філію","p.pending_location='BRANCH'"),
  branch("issue","Готові до видачі","p.current_location='BRANCH' AND p.pending_location IS NULL"),
  new Task("approval","manager","Погодити ціну","виробів","approval",PRODUCT_FROM,PRODUCT_COLUMNS,ACTIVE+" AND "+APPROVAL,"o.OrderNo,p.id"),
  new Task("contacts","manager","Заплановані контакти","клієнтів / філій","customers"," FROM crm_customer_profiles cp JOIN customer c ON c.CustNo=cp.customer_id JOIN branches b ON b.BranchNo=cp.branch_no ","c.CustNo AS customerId,c.Company AS customerName,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS phone,b.BranchName AS branchName,cp.branch_no AS branchNo,cp.next_contact_date AS contactDate","cp.next_contact_date IS NOT NULL AND cp.next_contact_date<=?","cp.next_contact_date,c.CustNo,cp.branch_no"),
  product("new","Нові вироби","p.production_state='WAITING'"),
  product("measure","Прийняті без обміру","p.production_state='ACCEPTED' AND (p.measured_width_mm IS NULL OR p.measured_height_mm IS NULL)"),
  product("work","У роботі","p.production_state='IN_PROGRESS'"),
  product("waiting","Очікують погодження",OPEN+" AND "+APPROVAL),
  product("shortage","Дефіцит матеріалів",OPEN+" AND EXISTS(SELECT 1 FROM material_reservations r WHERE r.order_product_id=p.id AND r.active=1 AND r.demand>r.reserved)"),
  product("transfer","Готові до передачі","p.production_state='COMPLETED' AND p.production_confirmed=1 AND p.current_location='WORKSHOP' AND p.pending_location IS NULL AND p.active_rework_id IS NULL")
 );
 private User user(Authentication a){
  if(a==null||!a.isAuthenticated())throw denied();
  var u=users.findByUsername(a.getName()).orElseThrow(TaskService::denied);
  if(!u.isEnabled())throw denied();return u;
 }
 private static boolean role(User u,String... roles){return u.getRoles().stream().anyMatch(r->Set.of(roles).contains(r.getName()));}
 static List<String> groups(User u){var groups=new ArrayList<String>();
  if(role(u,"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER"))groups.add("manager");
  if(role(u,"ROLE_ADMIN","ROLE_FOREMAN"))groups.add("workshop");
  if(role(u,"ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER"))groups.add("accountant");
  if(role(u,"ROLE_ADMIN"))groups.add("admin");
  if(role(u,"ROLE_WAREHOUSE"))groups.add("warehouse");return groups;
 }
 // CRM retains its existing assigned-branch scope even for administrators.
 private String scope(User u,Task t,List<Object> args){
  if(role(u,"ROLE_ADMIN")&&!t.key().equals("contacts"))return "";
  var ids=u.getAllowedBranches().stream().map(b->b.getBranchNo()).distinct().sorted().toList();
  if(ids.isEmpty())return " AND 1=0 ";args.addAll(ids);
  return " AND "+(t.key().equals("contacts")?"cp.branch_no":"o.BranchNo")+" IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ";
 }
 private List<Object> args(Task t){var args=new ArrayList<Object>();if(t.key().equals("contacts"))args.add(LocalDate.now(ZoneId.of("Europe/Kyiv")));return args;}
 public Object overview(Authentication a){var u=user(a);var allowed=groups(u);var cards=new ArrayList<Map<String,Object>>();
  for(var t:TASKS){if(!allowed.contains(t.group()))continue;var args=args(t);String filter=scope(u,t,args);Long count=db.queryForObject("SELECT COUNT(*)"+t.from()+" WHERE "+t.condition()+filter,Long.class,args.toArray());cards.add(Map.of("key",t.key(),"group",t.group(),"title",t.title(),"unit",t.unit(),"target",t.target(),"count",Objects.requireNonNullElse(count,0L)));}
  return Map.of("groups",allowed,"cards",cards,"asOf",OffsetDateTime.now(ZoneId.of("Europe/Kyiv")),"scope","Дозволені філії; контакти — за філіями, призначеними користувачу");
 }
 public Object queue(String key,int page,Authentication a){var u=user(a);
  if(page<0||page>100000)throw new PreviewException(HttpStatus.BAD_REQUEST,"TASK_PAGE","Некоректна сторінка");
  var t=TASKS.stream().filter(v->v.key().equals(key)).findFirst().orElseThrow(TaskService::denied);
  if(!groups(u).contains(t.group()))throw denied();
  var args=args(t);String filter=scope(u,t,args);args.add(page*20);
  var rows=db.queryForList("SELECT "+t.columns()+t.from()+" WHERE "+t.condition()+filter+" ORDER BY "+t.ordering()+" LIMIT 21 OFFSET ?",args.toArray());
  return Map.of("rows",rows.subList(0,Math.min(rows.size(),20)),"hasMore",rows.size()>20,"page",page);
 }
 private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"TASK_ACCESS","Немає доступу до цих завдань");}
}
