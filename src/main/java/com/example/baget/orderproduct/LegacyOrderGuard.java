package com.example.baget.orderproduct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.example.baget.util.TransactionException;
/** Called inside legacy write transactions. New orders must not bypass reservation accounting. */
@Component
public class LegacyOrderGuard {
    private final JdbcTemplate db;
    public LegacyOrderGuard(JdbcTemplate db){this.db=db;}
    public void check(Long id) {
        if(id==null)return;
        var revisions=db.queryForList("SELECT calculation_revision FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id);
        if(!revisions.isEmpty() && revisions.get(0)>0)
            throw new TransactionException("Це замовлення має вироби та резерви. Використайте нову форму розрахунку для редагування/скасування.");
    }
}
