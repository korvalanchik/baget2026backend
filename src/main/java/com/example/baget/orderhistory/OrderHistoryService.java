package com.example.baget.orderhistory;

import com.example.baget.users.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class OrderHistoryService {
 private final JdbcTemplate db;private final UsersRepository users;
 public OrderHistoryService(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 private static final Set<String> ROLES=Set.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER","ROLE_FOREMAN","ROLE_WAREHOUSE","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER");
 private User user(Authentication a){
  if(a==null || !a.isAuthenticated())throw denied();
  var u=users.findByUsername(a.getName()).orElseThrow(OrderHistoryService::denied);
  if(!u.isEnabled() || u.getRoles().stream().noneMatch(r->ROLES.contains(r.getName())))throw denied();return u;
 }
 private static boolean role(User u,String...names){return u.getRoles().stream().anyMatch(r->Arrays.asList(names).contains(r.getName()));}
 private String scope(User u,List<Object> args){
  if(role(u,"ROLE_ADMIN"))return "";
  var ids=u.getAllowedBranches().stream().map(x->x.getBranchNo()).distinct().sorted().toList();
  if(ids.isEmpty())return " AND 1=0";args.addAll(ids);
  return " AND o.BranchNo IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
 }
 private static final String HEADER="SELECT o.OrderNo AS orderNo,o.BranchNo AS branchNo,o.StatusOrder AS status,o.commerce_mode AS commerceMode,c.Company AS company,COALESCE(NULLIF(c.Mobile,''),c.Phone) AS phone,b.BranchName AS branchName,o.ShipDate AS dueDate FROM orders o JOIN customer c ON c.CustNo=o.CustNo JOIN branches b ON b.BranchNo=o.BranchNo ";
 public Object search(String q,int page,Authentication a){
  var u=user(a);q=q==null?"":q.trim();checkPage(page);
  if(q.length()>100)throw bad();if(q.length()<2)return Map.of("rows",List.of(),"hasMore",false);
  var args=new ArrayList<Object>();String allowed=scope(u,args);
  String digits=q.replaceAll("[^0-9]","");
  String phone=digits.length()>=3?digits:"!no-phone-match!";
  args.add(q);args.add(q);args.add(phone);args.add(phone);args.add(page*30);
  var rows=db.queryForList(HEADER+" WHERE 1=1"+allowed+" AND (CAST(o.OrderNo AS CHAR)=? OR LOCATE(?,c.Company)>0 OR LOCATE(?,REGEXP_REPLACE(COALESCE(c.Mobile,''),'[^0-9]',''))>0 OR LOCATE(?,REGEXP_REPLACE(COALESCE(c.Phone,''),'[^0-9]',''))>0) ORDER BY o.OrderNo DESC LIMIT 31 OFFSET ?",args.toArray());
  return Map.of("rows",rows.subList(0,Math.min(30,rows.size())),"hasMore",rows.size()>30);
 }
 public Object read(long order,String group,Long product,int page,Authentication a){
  var u=user(a);checkPage(page);if(order<=0 || (product!=null && product<=0))throw bad();
  if(!Set.of("ALL","PRODUCTION","MOVEMENT","PRICE","MATERIALS","CONTACT","FINANCE").contains(group))throw bad();
  var args=new ArrayList<Object>();args.add(order);String allowed=scope(u,args);
  var headers=db.queryForList(HEADER+" WHERE o.OrderNo=?"+allowed,args.toArray());
  if(headers.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Замовлення не знайдено або немає доступу");
  var header=headers.get(0);
  boolean contacts=role(u,"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER");
  boolean finance=role(u,"ROLE_ADMIN","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER") || ("OWN".equals(header.get("commerceMode")) && contacts);
  if((group.equals("CONTACT")&&!contacts)||(group.equals("FINANCE")&&!finance))throw denied();
  var products=db.queryForList("SELECT id,client_product_id AS productName,revision,active,production_state AS state,current_location AS location,pending_location AS pending FROM order_products WHERE order_no=? ORDER BY client_product_id,revision DESC",order);
  if(product!=null && products.stream().noneMatch(x->((Number)x.get("id")).longValue()==product))throw bad();
  var queries=new ArrayList<String>();var values=new ArrayList<Object>();
  add(queries,values,"SELECT CONVERT(CONCAT('order:',o.OrderNo) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS eventKey,o.SaleDate AS happenedAt,CONVERT('PRODUCTION' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS category,CONVERT('CREATED' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS action,NULL AS productId,NULL AS productName,CONVERT(u.username USING utf8mb4) COLLATE utf8mb4_unicode_ci AS actor,NULL AS note,NULL AS fromLocation,NULL AS toLocation,NULL AS amount,NULL AS direction FROM orders o LEFT JOIN users u ON u.id=o.EmpNo WHERE o.OrderNo=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('version:',p.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.created_at,CONVERT('PRODUCTION' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(CASE WHEN p.revision=1 THEN 'PRODUCT_CREATED' ELSE 'PRODUCT_REVISED' END USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.id,CONVERT(p.client_product_id USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,CONVERT(CONCAT('Версія ',p.revision) USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM order_products p WHERE p.order_no=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('production:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('PRODUCTION' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.action USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.id,CONVERT(p.client_product_id USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.note USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM product_production_events e JOIN order_products p ON p.id=e.order_product_id WHERE p.order_no=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('legacy:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('PRODUCTION' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.action USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.note USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM production_events e WHERE e.order_no=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('move:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('MOVEMENT' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.action USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.id,CONVERT(p.client_product_id USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.note USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.from_location USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.to_location USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL FROM product_transfers e JOIN order_products p ON p.id=e.product_id WHERE p.order_no=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('price:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('PRICE' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.action USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.id,CONVERT(p.client_product_id USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.note USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM product_price_events e JOIN order_products p ON p.id=e.product_id WHERE p.order_no=?",order);
  add(queries,values,"SELECT CONVERT(CONCAT('reserve:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('MATERIALS' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.reason USING utf8mb4) COLLATE utf8mb4_unicode_ci,p.id,CONVERT(p.client_product_id USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(CONCAT(pa.Description,': резерв ',e.reserved_before,' → ',e.reserved_after) USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM reservation_events e JOIN material_reservations r ON r.id=e.reservation_id JOIN order_products p ON p.id=r.order_product_id JOIN parts pa ON pa.PartNo=r.part_no WHERE p.order_no=?",order);
  if(contacts)add(queries,values,"SELECT CONVERT(CONCAT('contact:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('CONTACT' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.outcome USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,CONVERT(e.actor USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.note USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,NULL FROM crm_contacts e WHERE e.order_no=? AND e.branch_no=?",order,header.get("branchNo"));
  // Only explicitly order-linked ledger entries; a shared invoice payment or client advance is not this order's payment.
  if(finance)add(queries,values,"SELECT CONVERT(CONCAT('ledger:',e.id) USING utf8mb4) COLLATE utf8mb4_unicode_ci,e.created_at,CONVERT('FINANCE' USING utf8mb4) COLLATE utf8mb4_unicode_ci,CONVERT(e.category USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,CONVERT(u.username USING utf8mb4) COLLATE utf8mb4_unicode_ci,NULL,NULL,NULL,e.amount,e.direction FROM ledger_entries e LEFT JOIN users u ON u.id=e.created_by WHERE e.order_id=? AND e.branch_id=? AND e.category IN ('INVOICE_ISSUED','PAYMENT_RECEIVED','CUSTOMER_PAYMENT','INTERNAL_TRANSFER_IN','INTERNAL_TRANSFER_OUT','ADJUSTMENT','CUSTOMER_REFUND')",order,header.get("branchNo"));
  String filter="";if(!group.equals("ALL")){filter+=" AND category=?";values.add(group);}if(product!=null){filter+=" AND productId=?";values.add(product);}values.add(page*50);
  var rows=db.queryForList("SELECT * FROM ("+String.join(" UNION ALL ",queries)+") history WHERE 1=1"+filter+" ORDER BY happenedAt DESC,eventKey DESC LIMIT 51 OFFSET ?",values.toArray());
  return Map.of("order",header,"products",products,"rows",rows.subList(0,Math.min(50,rows.size())),"hasMore",rows.size()>50,"canContacts",contacts,"canFinance",finance);
 }
 private static void add(List<String> queries,List<Object> values,String sql,Object...args){queries.add(sql);values.addAll(Arrays.asList(args));}
 private static void checkPage(int page){if(page<0||page>10000)throw bad();}
 private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Немає доступу до історії");}
 private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Перевірте пошук та фільтри історії");}
}
