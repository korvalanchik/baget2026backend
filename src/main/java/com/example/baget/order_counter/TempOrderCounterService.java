package com.example.baget.order_counter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TempOrderCounterService {
    private final JdbcTemplate db;
    public TempOrderCounterService(JdbcTemplate db){this.db=db;}
    @Transactional public Long getNextOrderNumber() {
        long current=db.queryForObject("SELECT last_temp_order_no FROM temp_order_counter WHERE id=1 FOR UPDATE",Long.class);
        long next=current+1;
        db.update("UPDATE temp_order_counter SET last_temp_order_no=? WHERE id=1",next);
        return next;
    }
}
