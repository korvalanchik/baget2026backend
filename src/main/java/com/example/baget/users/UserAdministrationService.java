package com.example.baget.users;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class UserAdministrationService {
 private final JdbcTemplate db;private final PasswordEncoder encoder;private final ObjectMapper json;
 public UserAdministrationService(JdbcTemplate db,PasswordEncoder encoder,ObjectMapper json){this.db=db;this.encoder=encoder;this.json=json;}
 public record Edit(@Positive Long id,@PositiveOrZero long revision,@NotBlank @Size(max=100) String username,@NotBlank @Email @Size(max=100) String email,boolean enabled,@Size(max=72) String password,@NotEmpty @Size(max=50) Set<@NotNull @Positive Long> roles,@NotNull @Size(max=500) Set<@NotNull @Positive Long> branches){}
 private PreviewException bad(String message){return new PreviewException(HttpStatus.CONFLICT,"USER_EDIT",message);}
 private long access(Authentication a){if(a==null||!a.isAuthenticated())throw denied();var ids=db.queryForList("SELECT u.id FROM users u WHERE u.username=? AND u.enabled=1 AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.name='ROLE_ADMIN')",Long.class,a.getName());if(ids.isEmpty())throw denied();return ids.get(0);}
 private PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"USER_ACCESS","Керування користувачами доступне адміністратору");}
 public Object options(Authentication a){long self=access(a);return Map.of("selfId",self,"roles",db.queryForList("SELECT id,name FROM roles ORDER BY name"),"branches",db.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName"));}
 public Object list(String q,int page,Authentication a){access(a);String query=q==null?"":q.trim();if(query.length()>100||page<0||page>100000)throw bad("Некоректний пошук");var rows=db.queryForList("SELECT id,username,email,enabled,access_revision AS revision FROM users WHERE LOCATE(?,username)>0 OR LOCATE(?,email)>0 ORDER BY username,id LIMIT 51 OFFSET ?",query,query,page*50);boolean more=rows.size()>50;if(more)rows=new ArrayList<>(rows.subList(0,50));for(var r:rows)relations(r);return Map.of("rows",rows,"hasMore",more);}
 private void relations(Map<String,Object> r){r.put("roles",db.queryForList("SELECT role_id FROM user_roles WHERE user_id=? ORDER BY role_id",Long.class,r.get("id")));r.put("branches",db.queryForList("SELECT branch_no FROM user_branch_access WHERE user_id=? ORDER BY branch_no",Long.class,r.get("id")));}
 public Object save(Edit e,Authentication a){access(a);db.queryForObject("SELECT id FROM user_administration_lock WHERE id=1 FOR UPDATE",Integer.class);long self=access(a);
  var roleRows=db.queryForList("SELECT id,name FROM roles");var validRoles=new HashSet<Long>();Long adminRole=null;for(var r:roleRows){long id=((Number)r.get("id")).longValue();validRoles.add(id);if("ROLE_ADMIN".equals(r.get("name")))adminRole=id;}
  if(!validRoles.containsAll(e.roles()))throw bad("Невідома роль");
  boolean admin=adminRole!=null&&e.roles().contains(adminRole);
  var validBranches=new HashSet<>(db.queryForList("SELECT BranchNo FROM branches",Long.class));if(!validBranches.containsAll(e.branches()))throw bad("Невідома філія");if(e.enabled()&&!admin&&e.branches().isEmpty())throw bad("Для активного користувача оберіть доступні філії");
  boolean password=e.password()!=null&&!e.password().isEmpty();if((e.id()==null&&!password)||(password&&(e.password().length()<10||e.password().getBytes(StandardCharsets.UTF_8).length>72)))throw bad("Пароль: від 10 символів, не більше 72 байтів UTF-8");
  Map<String,Object> before=null;long id;
  if(e.id()!=null){id=e.id();var found=db.queryForList("SELECT id,username,email,enabled,access_revision AS revision FROM users WHERE id=? FOR UPDATE",id);if(found.isEmpty())throw bad("Користувача не знайдено");before=found.get(0);relations(before);if(((Number)before.get("revision")).longValue()!=e.revision())throw bad("Дані змінилися. Оновіть список");if(!e.username().equals(before.get("username")))throw bad("Логін існуючого користувача не змінюється");
   if(id==self&&(!e.enabled()||!admin))throw bad("Не можна заблокувати себе або зняти власну роль адміністратора");
   if(!e.enabled()||!admin){long count=db.queryForObject("SELECT COUNT(*) FROM users u WHERE u.enabled=1 AND u.id<>? AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.name='ROLE_ADMIN')",Long.class,id);if(count==0)throw bad("Потрібен хоча б один активний адміністратор");}
   if(!db.queryForList("SELECT id FROM users WHERE email=? AND id<>?",e.email().trim(),id).isEmpty())throw bad("Ця електронна адреса вже використовується");
   db.update("UPDATE users SET email=?,enabled=?,access_revision=access_revision+1 WHERE id=?",e.email().trim(),e.enabled(),id);
   if(password)db.update("UPDATE users SET password=? WHERE id=?",encoder.encode(e.password()),id);
   if(password||!e.enabled())db.update("DELETE FROM password_recovery_tokens WHERE user_id=?",id);
  }else{
   if(!e.username().matches("[A-Za-z0-9._@-]{3,100}"))throw bad("Новий логін: 3–100 латинських літер, цифр або . _ @ -");
   if(!db.queryForList("SELECT id FROM users WHERE username=? OR email=?",e.username(),e.email().trim()).isEmpty())throw bad("Логін або електронна адреса вже використовується");
   db.update("INSERT INTO users(username,email,password,enabled,access_revision) VALUES(?,?,?,?,0)",e.username(),e.email().trim(),encoder.encode(e.password()),e.enabled());id=db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,e.username());
  }
  db.update("DELETE FROM user_roles WHERE user_id=?",id);for(long role:new TreeSet<>(e.roles()))db.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)",id,role);
  db.update("DELETE FROM user_branch_access WHERE user_id=?",id);for(long branch:new TreeSet<>(e.branches()))db.update("INSERT INTO user_branch_access(user_id,branch_no) VALUES(?,?)",id,branch);
  var after=new LinkedHashMap<String,Object>();after.put("id",id);after.put("username",e.username());after.put("email",e.email().trim());after.put("enabled",e.enabled());after.put("roles",e.roles());after.put("branches",e.branches());after.put("passwordChanged",password);
  var snapshot=new LinkedHashMap<String,Object>();snapshot.put("before",before);snapshot.put("after",after);
  db.update("INSERT INTO user_administration_events(user_id,actor,action,snapshot) VALUES(?,?,?,?)",id,a.getName(),e.id()==null?"CREATE":"EDIT",encode(snapshot));return Map.of("id",id,"selfChanged",id==self);
 }
 public Object history(long id,int page,Authentication a){access(a);if(page<0||page>100000)throw bad("Некоректна сторінка");var rows=db.queryForList("SELECT id,actor,action,created_at AS createdAt,snapshot FROM user_administration_events WHERE user_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",id,page*20);rows.forEach(r->{try{r.put("snapshot",json.readTree(r.get("snapshot").toString()));}catch(Exception ex){throw new IllegalStateException(ex);}});return rows;}
 private String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception ex){throw new IllegalStateException(ex);}}
}
