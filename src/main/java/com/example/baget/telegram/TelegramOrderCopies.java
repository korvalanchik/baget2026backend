package com.example.baget.telegram;

import com.example.baget.users.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;

@Service
public class TelegramOrderCopies {
 private final JdbcTemplate db;private final UsersRepository users;private final ObjectMapper json;private final String adminChat;
 public TelegramOrderCopies(JdbcTemplate db,UsersRepository users,ObjectMapper json,@Value("${CHAT_ID:}") String adminChat){this.db=db;this.users=users;this.json=json;this.adminChat=adminChat;}
 @EventListener
 @Transactional(propagation=Propagation.MANDATORY)
 public void created(OrderCopyCreated event){
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT enabled FROM telegram_copy_settings WHERE id=1",Boolean.class)))return;
  var order=db.queryForMap("SELECT o.OrderNo,o.EmpNo,o.commerce_mode,o.commerce_partner_id,b.BranchName,o.ItemsTotal,o.ShipDate,o.Notice FROM orders o JOIN branches b ON b.BranchNo=o.BranchNo WHERE o.OrderNo=?",event.orderNo());
  var chats=destinations(order);
  String manager=order.get("EmpNo") instanceof Number authorId ? users.findById(authorId.longValue()).map(User::getUsername).orElse("—") : "—";
  StringBuilder text=new StringBuilder("Замовлення № "+event.orderNo()+"\nФілія: "+order.get("BranchName")+"\nМенеджер: "+manager+"\n");
  if(order.get("ShipDate")!=null)text.append("Готовність: ").append(order.get("ShipDate").toString(),0,10).append('\n');
  for(var p:db.queryForList("SELECT id,client_product_id,configuration,total FROM order_products WHERE order_no=? AND active=1 ORDER BY id",event.orderNo())){
   text.append("\nВиріб: ").append(p.get("client_product_id")).append('\n');
   try{var draft=json.readTree(p.get("configuration").toString()).path("draft");
    boolean size=false;
    for(var component:draft.path("components")){
     var product=component.path("product");var source=product.isObject()?product:component.path("mounting");
     if(!size){text.append("Розмір: ").append(source.path("widthMm").asText()).append(" × ").append(source.path("heightMm").asText()).append(" мм\nКількість: ").append(source.path("productQuantity").asText("1")).append('\n');size=true;}
     if(product.isObject()){
      if(product.path("framePartNo").isMissingNode() || product.path("framePartNo").isNull())text.append("Рама: без рами\n");
      var m=product.path("matMargins");if(m.isObject())text.append("Паспарту, поля ліве/праве/верх/низ: ").append(m.path("leftMm").asText()).append('/').append(m.path("rightMm").asText()).append('/').append(m.path("topMm").asText()).append('/').append(m.path("bottomMm").asText()).append(" мм\n");
     }
    }
    if(!draft.path("note").asText("").isBlank())text.append("Примітка виробу: ").append(draft.path("note").asText()).append('\n');
   }catch(Exception e){throw new IllegalStateException("Cannot format saved order configuration",e);}
   for(var line:db.queryForList("SELECT p.Description,i.planned_consumption,i.calculation_unit FROM items i JOIN parts p ON p.PartNo=i.PartNo WHERE i.order_product_id=? ORDER BY i.ItemNo",p.get("id")))
    text.append("• ").append(line.get("Description")).append(": ").append(number(line.get("planned_consumption"))).append(' ').append(unit(line.get("calculation_unit"))).append('\n');
   text.append("Сума виробу: ").append(p.get("total")).append(" грн\n");
  }
  if(order.get("Notice")!=null && !order.get("Notice").toString().isBlank())text.append("\nПримітка: ").append(order.get("Notice")).append('\n');
  text.append("\nРазом: ").append(order.get("ItemsTotal")).append(" грн");
  var chunks=TelegramCopyText.split(text.toString());
  for(long chat:chats.keySet())for(int i=0;i<chunks.size();i++)db.update("INSERT INTO telegram_order_outbox(order_no,chat_id,part_no,body,recipient) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",event.orderNo(),chat,i+1,(chunks.size()>1 ? "Замовлення № "+event.orderNo()+" · частина "+(i+1)+"/"+chunks.size()+"\n" : "")+chunks.get(i),chats.get(chat));
 }
 public boolean allowed(long orderNo,long chat){
  var rows=db.queryForList("SELECT EmpNo,commerce_mode,commerce_partner_id FROM orders WHERE OrderNo=?",orderNo);
  return !rows.isEmpty() && destinations(rows.get(0)).containsKey(chat);
 }
 private Map<Long,String> destinations(Map<String,Object> order){
  var chats=new TreeMap<Long,String>();
  if(!adminChat.isBlank())try{chats.put(Long.valueOf(adminChat.trim()),"Адміністративний чат");}catch(NumberFormatException ignored){}
  var representatives=new HashSet<Long>();
  if("RESELLER".equals(order.get("commerce_mode")) && order.get("commerce_partner_id")!=null)
   representatives.addAll(db.queryForList("SELECT user_id FROM telegram_partner_recipients WHERE partner_id=?",Long.class,order.get("commerce_partner_id")));
  for(var u:users.findAll()){
   boolean author=order.get("EmpNo") instanceof Number n && n.longValue()==u.getId();
   if(u.isEnabled() && u.getTelegramId()!=null && (author || TelegramCopySettings.admin(u) || representatives.contains(u.getId())))chats.put(u.getTelegramId(),u.getUsername());
  }
  return chats;
 }
 private static String number(Object x){return x==null?"—":new java.math.BigDecimal(x.toString()).stripTrailingZeros().toPlainString();}
 private static String unit(Object x){return switch(String.valueOf(x)){case "M","METER"->"м";case "M2","SQUARE_METER"->"м²";case "PCS","PIECE"->"шт.";default->String.valueOf(x);};}
}
