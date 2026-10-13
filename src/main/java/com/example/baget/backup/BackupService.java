package com.example.baget.backup;

import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class BackupService {
 private final JdbcTemplate db;
 private final UsersRepository users;
 public BackupService(JdbcTemplate db, UsersRepository users) { this.db=db; this.users=users; }
 void admin(Authentication a) {
  if(a==null || !a.isAuthenticated()) throw denied();
  var u=users.findByUsername(a.getName()).orElseThrow(BackupService::denied);
  if(!u.isEnabled() || u.getRoles().stream().noneMatch(r->"ROLE_ADMIN".equals(r.getName()))) throw denied();
 }
 public record Settings(boolean enabled,String frequency,int weekday,String time,int keepCount,long revision) {}
 public static void validate(Settings r) {
  if(r==null || !("DAILY".equals(r.frequency()) || "WEEKLY".equals(r.frequency())) || r.weekday()<1 || r.weekday()>7 || r.time()==null || !r.time().matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || r.keepCount()<2 || r.keepCount()>365 || r.revision()<0)
   throw bad("Перевірте розклад і кількість копій (2–365)");
 }
 public Map<String,Object> read(Authentication a) {
  admin(a);
  var s=db.queryForMap("SELECT enabled,frequency,weekday,local_time AS time,keep_count AS keepCount,revision,DATE_FORMAT(checked_at,'%Y-%m-%dT%H:%i:%sZ') AS checkedAt,storage_status AS storageStatus,storage_message AS storageMessage FROM backup_settings WHERE id=1");
  return Map.of("settings",s,"runs",db.queryForList("SELECT id,kind,actor,state,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') AS createdAt,DATE_FORMAT(started_at,'%Y-%m-%dT%H:%i:%sZ') AS startedAt,DATE_FORMAT(finished_at,'%Y-%m-%dT%H:%i:%sZ') AS finishedAt,file_name AS fileName,bytes,sha256,message FROM backup_runs ORDER BY created_at DESC,id DESC LIMIT 100"));
 }
 @Transactional public Map<String,Object> save(Settings r,Authentication a) {
  admin(a);validate(r);
  var old=db.queryForMap("SELECT revision,enabled,frequency,weekday,local_time FROM backup_settings WHERE id=1 FOR UPDATE");
  if(((Number)old.get("revision")).longValue()!=r.revision()) throw bad("Налаштування вже змінено. Оновіть форму");
  // Editing retention alone must not cause the same day's scheduled backup to repeat.
  boolean scheduleChanged=!Objects.equals(old.get("frequency"),r.frequency()) || ((Number)old.get("weekday")).intValue()!=r.weekday() || !Objects.equals(old.get("local_time"),r.time()) || truth(old.get("enabled"))!=r.enabled();
  db.update("UPDATE backup_settings SET enabled=?,frequency=?,weekday=?,local_time=?,keep_count=?,revision=revision+1,updated_by=?,schedule_from=CASE WHEN ? THEN UTC_TIMESTAMP(6) ELSE schedule_from END WHERE id=1",r.enabled(),r.frequency(),r.weekday(),r.time(),r.keepCount(),a.getName(),scheduleChanged);
  return read(a);
 }
 @Transactional public String enqueue(String key,Authentication a) {
  admin(a);
  try { if(key==null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); } catch(IllegalArgumentException e) { throw bad("Некоректний ключ запиту"); }
  db.queryForMap("SELECT id FROM backup_settings WHERE id=1 FOR UPDATE");
  var existing=db.queryForList("SELECT id,actor FROM backup_runs WHERE request_key=?","manual:"+key);
  if(!existing.isEmpty()) { if(!Objects.equals(existing.get(0).get("actor"),a.getName())) throw denied(); return existing.get(0).get("id").toString(); }
  var count=db.queryForObject("SELECT COUNT(*) FROM backup_runs WHERE state IN ('QUEUED','RUNNING')",Long.class);
  if(count!=null && count>0) throw bad("Копія вже очікує запуску або створюється. Оновіть історію");
  var id=UUID.randomUUID().toString();
  db.update("INSERT INTO backup_runs(id,request_key,kind,actor) VALUES(?,?,'MANUAL',?)",id,"manual:"+key,a.getName());
  return id;
 }
 public Map<String,Object> file(String id,Authentication a) {
  admin(a);
  var rows=db.queryForList("SELECT file_id,file_name FROM backup_runs WHERE id=? AND state='SUCCESS'",id);
  if(rows.isEmpty()) throw bad("Успішну збережену копію не знайдено");
  return rows.get(0);
 }
 static boolean truth(Object v) { return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()!=0; }
 static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"BACKUP_ACCESS","Резервні копії доступні лише адміністратору");}
 static PreviewException bad(String m){return new PreviewException(HttpStatus.CONFLICT,"BACKUP_SETTINGS",m);}
}
