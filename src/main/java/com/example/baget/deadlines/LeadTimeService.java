package com.example.baget.deadlines;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class LeadTimeService {
 private final JdbcTemplate db;private final UsersRepository users;
 public LeadTimeService(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
 User user(Authentication a){if(a==null||!a.isAuthenticated())throw denied();var u=users.findByUsername(a.getName()).orElseThrow(LeadTimeService::denied);if(!u.isEnabled()||!role(u,"ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_FOREMAN","ROLE_WAREHOUSE","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_COUNTER"))throw denied();return u;}
 static boolean role(User u,String... roles){return u.getRoles().stream().anyMatch(r->Set.of(roles).contains(r.getName()));}
 public record Settings(int minDays,int normalDays,int shortageDays,int upcomingDays,long revision){}
 public static void validate(Settings r){if(r==null||r.minDays()<1||r.minDays()>r.normalDays()||r.normalDays()>r.shortageDays()||r.shortageDays()>365||r.upcomingDays()<1||r.upcomingDays()>30||r.revision()<0)throw bad("Перевірте строки: 1 ≤ мінімальний ≤ звичайний ≤ за дефіциту ≤ 365; огляд — 1–30 робочих днів");}
 public static LocalDate addWorkingDays(LocalDate start,int count){if(count<0||count>365)throw new IllegalArgumentException("days");while(count>0){start=start.plusDays(1);if(start.getDayOfWeek()!=DayOfWeek.SATURDAY&&start.getDayOfWeek()!=DayOfWeek.SUNDAY)count--;}return start;}
 public Settings current(){var r=db.queryForMap("SELECT min_days,normal_days,shortage_days,upcoming_days,revision FROM lead_time_settings WHERE id=1");return new Settings(((Number)r.get("min_days")).intValue(),((Number)r.get("normal_days")).intValue(),((Number)r.get("shortage_days")).intValue(),((Number)r.get("upcoming_days")).intValue(),((Number)r.get("revision")).longValue());}
 public Settings read(Authentication a){user(a);return current();}
 @Transactional public Settings save(Settings r,Authentication a){var u=user(a);if(!role(u,"ROLE_ADMIN"))throw denied();validate(r);int changed=db.update("UPDATE lead_time_settings SET min_days=?,normal_days=?,shortage_days=?,upcoming_days=?,revision=revision+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=1 AND revision=?",r.minDays(),r.normalDays(),r.shortageDays(),r.upcomingDays(),u.getUsername(),r.revision());if(changed!=1)throw bad("Налаштування вже змінено. Оновіть форму та перевірте значення");return current();}
 static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"DEADLINE_ACCESS","Немає доступу до строків / завдань");}
 static PreviewException bad(String m){return new PreviewException(HttpStatus.CONFLICT,"DEADLINE_SETTINGS",m);}
}
