package com.example.baget.telegram;

import com.example.baget.users.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class TelegramCopySettings {
 private final JdbcTemplate db; private final UsersRepository users;
 public TelegramCopySettings(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 static boolean admin(User u){return u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName()));}
 void authorize(Authentication a){
  if(a==null || !a.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Немає доступу");
  User u=users.findByUsername(a.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
  if(!u.isEnabled() || !admin(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Лише адміністратор");
 }
 public record Recipient(long partnerId,long userId){}
 public record Update(boolean enabled,long revision,List<Recipient> recipients){}
 public Map<String,Object> read(Authentication a){authorize(a);var result=new LinkedHashMap<String,Object>(db.queryForMap("SELECT enabled,revision FROM telegram_copy_settings WHERE id=1"));
  result.put("recipients",db.queryForList("SELECT partner_id AS partnerId,user_id AS userId FROM telegram_partner_recipients ORDER BY partner_id,user_id"));
  result.put("partners",db.queryForList("SELECT DISTINCT c.CustNo AS id,c.Company AS name FROM branch_commercial_settings s JOIN customer c ON c.CustNo=s.partner_id WHERE s.mode='RESELLER' ORDER BY c.Company"));
  result.put("users",users.findAll().stream().filter(User::isEnabled).map(u->{var m=new LinkedHashMap<String,Object>();m.put("id",u.getId());m.put("name",u.getUsername());m.put("linked",u.getTelegramId()!=null);return m;}).toList());
  result.put("deliveries",db.queryForList("SELECT id,recipient,order_no AS orderNo,part_no AS partNo,state,attempts,last_error AS error,created_at AS createdAt,sent_at AS sentAt FROM telegram_order_outbox ORDER BY id DESC LIMIT 100"));return result;
 }
 @Transactional
 public Map<String,Object> save(Update r,Authentication a){authorize(a);
  if(r==null || r.recipients()==null || r.recipients().size()>500 || r.revision()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Перевірте налаштування");
  var unique=new HashSet<Recipient>();
  for(var x:r.recipients()){
   if(x==null || !unique.add(x) || db.queryForObject("SELECT COUNT(*) FROM branch_commercial_settings WHERE mode='RESELLER' AND partner_id=?",Long.class,x.partnerId())==0)
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Перевірте фірму та повтори отримувачів");
   var u=users.findById(x.userId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Користувача не знайдено"));
   if(!u.isEnabled() || u.getTelegramId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Отримувач має бути активним та мати Telegram ID");
  }
  if(db.update("UPDATE telegram_copy_settings SET enabled=?,revision=revision+1 WHERE id=1 AND revision=?",r.enabled(),r.revision())!=1)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Налаштування змінено. Оновіть сторінку");
  db.update("DELETE FROM telegram_partner_recipients");
  for(var x:unique)db.update("INSERT INTO telegram_partner_recipients(partner_id,user_id) VALUES(?,?)",x.partnerId(),x.userId());
  return read(a);
 }
 @Transactional public void retry(long id,Authentication a){authorize(a);db.update("UPDATE telegram_order_outbox SET state='PENDING',attempts=0,next_attempt=CURRENT_TIMESTAMP,last_error=NULL WHERE id=? AND state='FAILED'",id);}
}
