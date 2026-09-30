package com.example.baget.finance;

import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class CustomerPaymentAccess {
    private final UsersRepository users;
    private final JdbcTemplate db;
    public CustomerPaymentAccess(UsersRepository users, JdbcTemplate db) { this.users=users; this.db=db; }
    public User user(Authentication a) {
        if(a==null || !a.isAuthenticated()) throw denied();
        var u=users.findByUsername(a.getName()).orElseThrow(CustomerPaymentAccess::denied);
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER").contains(r.getName()))) throw denied();
        return u;
    }
    public boolean admin(User u) { return u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName())); }
    public void branch(User u,long branch) {
        if(!admin(u) && u.getAllowedBranches().stream().noneMatch(b->b.getBranchNo()==branch)) throw denied();
    }
    public void customer(User u,long customer,long branch) {
        branch(u,branch);
        Long count=db.queryForObject("SELECT COUNT(*) FROM customer c WHERE c.CustNo=? AND (EXISTS(SELECT 1 FROM orders o WHERE o.CustNo=c.CustNo AND o.BranchNo=?) OR EXISTS(SELECT 1 FROM invoices i JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no WHERE (i.customer_id=c.CustNo OR i.payer_id=c.CustNo) AND o.BranchNo=?))",Long.class,customer,branch,branch);
        if(count==null || count==0) throw denied();
    }
    public void invoice(User u,long id) {
        var branches=db.queryForList("SELECT DISTINCT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=?",Long.class,id);
        if(branches.isEmpty()) throw denied();
        for(long b:branches) branch(u,b);
        for(long b:db.queryForList("SELECT DISTINCT branch_id FROM customer_transactions WHERE invoice_id=? AND type IN ('INVOICE','INVOICE_MERGE_OUT')",Long.class,id)) branch(u,b);
    }
    private static PreviewException denied() { return new PreviewException(HttpStatus.FORBIDDEN,"CUSTOMER_PAYMENT_ACCESS","Немає права приймати оплату або доступу до клієнта чи філії"); }
}
