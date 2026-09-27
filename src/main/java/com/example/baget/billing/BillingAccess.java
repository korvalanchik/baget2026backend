package com.example.baget.billing;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class BillingAccess {
    private final UsersRepository users; private final JdbcTemplate db;
    public BillingAccess(UsersRepository users,JdbcTemplate db){this.users=users;this.db=db;}
    public User user(Authentication a){
        if(a==null || !a.isAuthenticated())throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var u=users.findByUsername(a.getName()).orElseThrow(()->denied());
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER").contains(r.getName())))throw denied();
        return u;
    }
    public boolean admin(User u){return u.getRoles().stream().anyMatch(r->r.getName().equals("ROLE_ADMIN"));}
    public void branch(User u,long id){if(!admin(u)&&u.getAllowedBranches().stream().noneMatch(b->b.getBranchNo()==id))throw denied();}
    public void invoice(User u,long id){
        var branches=db.queryForList("SELECT DISTINCT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=?",Long.class,id);
        if(branches.isEmpty())throw conflict("Рахунок не знайдено");for(long b:branches)branch(u,b);
        for(long b:db.queryForList("SELECT DISTINCT branch_id FROM customer_transactions WHERE invoice_id=? AND type IN ('INVOICE','INVOICE_MERGE_OUT')",Long.class,id))branch(u,b);
    }
    public static PreviewException conflict(String s){return new PreviewException(HttpStatus.CONFLICT,"PRODUCT_INVOICE_CONFLICT",s);}
    private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"BILLING_ACCESS_DENIED","Немає бухгалтерських прав або доступу до філії");}
}
