package com.example.baget.assortment;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class FrameAssortmentService {
    private final JdbcTemplate db;private final UsersRepository users;
    public FrameAssortmentService(JdbcTemplate db,UsersRepository users){this.db=db;this.users=users;}
    public record Change(@PositiveOrZero long expectedRevision,@NotBlank String status,@NotBlank @Size(max=1000) String note) {}
    private boolean access(Authentication a){
        if(a==null || !a.isAuthenticated())throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var user=users.findByUsername(a.getName()).orElseThrow(()->denied());
        var roles=user.getRoles().stream().map(r->r.getName()).toList();
        if(Collections.disjoint(roles,Set.of("ROLE_ADMIN","ROLE_FOREMAN","ROLE_WAREHOUSE")))throw denied();
        return roles.contains("ROLE_ADMIN");
    }
    private List<Map<String,Object>> branches(Authentication a){
        boolean admin=access(a);
        var rows=db.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName,BranchNo");
        if(admin)return rows;
        var names=users.findByUsername(a.getName()).orElseThrow().getAllowedBranches().stream().map(b->b.getName()).toList();
        return rows.stream().filter(b->names.contains(b.get("name"))).toList();
    }
    public static final String FRAME="(p.calculation_method='FRAME_PROFILE' OR (p.InQuality=2 AND p.ProfilWidth>0.0003))";
    public Map<String,Object> matrix(String query,int page,boolean activeOnly,Authentication a){
        query=query==null?"":query.trim();
        var branches=branches(a);
        var rows=db.queryForList("SELECT p.PartNo AS partNo,p.Description AS description,p.calculation_method AS method,p.unit_type AS unit,CAST(p.OnHand AS DECIMAL(24,6)) AS stock,COALESCE(s.activeCount,0) AS activeCount,COALESCE(f.status,'ALLOWED') AS purchaseStatus,COALESCE(f.revision,0) AS purchaseRevision FROM parts p LEFT JOIN (SELECT part_no,COUNT(*) AS activeCount FROM branch_frame_samples WHERE status='ACTIVE' GROUP BY part_no) s ON s.part_no=p.PartNo LEFT JOIN frame_purchase_policy f ON f.part_no=p.PartNo WHERE "+FRAME+" AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0) AND (?=0 OR COALESCE(s.activeCount,0)>0) ORDER BY p.PartNo LIMIT 50 OFFSET ?",query,query,activeOnly?1:0,Math.max(0,Math.min(page,100000))*50);
        var allowed=new HashSet<Long>();for(var b:branches)allowed.add(((Number)b.get("id")).longValue());
        if(!rows.isEmpty()){
            var ids=rows.stream().map(r->r.get("partNo")).toArray();
            var samples=db.queryForList("SELECT part_no AS partNo,branch_no AS branchNo,status,revision,note,actor,updated_at AS updatedAt FROM branch_frame_samples WHERE part_no IN ("+String.join(",",Collections.nCopies(ids.length,"?"))+")",ids);
            for(var row:rows)row.put("samples",samples.stream().filter(s->Objects.equals(s.get("partNo"),row.get("partNo"))&&allowed.contains(((Number)s.get("branchNo")).longValue())).toList());
        }
        return Map.of("branches",branches,"rows",rows);
    }
    private void lockPart(long part){
        if(db.queryForList("SELECT p.PartNo FROM parts p WHERE p.PartNo=? AND "+FRAME+" FOR UPDATE",Long.class,part).isEmpty())throw conflict("Профіль не знайдено");
    }
    public Map<String,Object> sample(long part,long branch,Change r,Authentication a){
        if(branches(a).stream().noneMatch(b->((Number)b.get("id")).longValue()==branch))throw denied();
        if(!Set.of("ACTIVE","PAUSED","REMOVED").contains(r.status()))throw conflict("Некоректний стан зразка");
        lockPart(part);
        var rows=db.queryForList("SELECT status,revision FROM branch_frame_samples WHERE part_no=? AND branch_no=?",part,branch);
        String previous=check(rows,r.expectedRevision());
        db.update("INSERT INTO branch_frame_samples(part_no,branch_no,status,revision,actor,note) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),revision=VALUES(revision),actor=VALUES(actor),note=VALUES(note),updated_at=CURRENT_TIMESTAMP(6)",part,branch,r.status(),r.expectedRevision()+1,a.getName(),r.note().trim());
        event(part,branch,previous,r,a);return Map.of("revision",r.expectedRevision()+1);
    }
    public Map<String,Object> purchase(long part,Change r,Authentication a){
        access(a);if(!Set.of("ALLOWED","DISCONTINUED","BLOCKED").contains(r.status()))throw conflict("Некоректний стан закупівлі");
        lockPart(part);var rows=db.queryForList("SELECT status,revision FROM frame_purchase_policy WHERE part_no=?",part);
        String previous=check(rows,r.expectedRevision());
        db.update("INSERT INTO frame_purchase_policy(part_no,status,revision,actor,note) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),revision=VALUES(revision),actor=VALUES(actor),note=VALUES(note),updated_at=CURRENT_TIMESTAMP(6)",part,r.status(),r.expectedRevision()+1,a.getName(),r.note().trim());
        event(part,null,previous,r,a);return Map.of("revision",r.expectedRevision()+1);
    }
    public List<Map<String,Object>> history(long part,Authentication a){
        var bs=branches(a);boolean admin=access(a);
        String scope="";var args=new ArrayList<Object>();args.add(part);
        if(!admin){scope=" AND (e.branch_no IS NULL";for(var b:bs){scope+=" OR e.branch_no=?";args.add(b.get("id"));}scope+=")";}
        return db.queryForList("SELECT e.branch_no AS branchNo,b.BranchName AS branchName,e.previous_status AS previousStatus,e.status,e.revision,e.actor,e.note,e.created_at AS createdAt FROM frame_assortment_events e LEFT JOIN branches b ON b.BranchNo=e.branch_no WHERE e.part_no=?"+scope+" ORDER BY e.id DESC LIMIT 200",args.toArray());
    }
    private String check(List<Map<String,Object>> rows,long expected){long current=rows.isEmpty()?0:((Number)rows.get(0).get("revision")).longValue();if(current!=expected)throw conflict("Набір уже змінено. Оновіть таблицю");return rows.isEmpty()?null:rows.get(0).get("status").toString();}
    private void event(long part,Long branch,String previous,Change r,Authentication a){db.update("INSERT INTO frame_assortment_events(part_no,branch_no,previous_status,status,revision,actor,note) VALUES(?,?,?,?,?,?,?)",part,branch,previous,r.status(),r.expectedRevision()+1,a.getName(),r.note().trim());}
    private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"ASSORTMENT_ACCESS_DENIED","Немає доступу до цієї філії або обліку зразків");}
    private static PreviewException conflict(String text){return new PreviewException(HttpStatus.CONFLICT,"ASSORTMENT_CONFLICT",text);}
}
