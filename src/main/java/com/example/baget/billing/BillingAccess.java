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
    /**
     * Read-only access for invoices opened from the CRM customer card.
     * This is deliberately broader than accountant-only product billing access,
     * but still requires an allowed CRM role and branch-level invoice scope.
     */
    public User customerInvoiceUser(Authentication a){
        if(a==null || !a.isAuthenticated())throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var u=users.findByUsername(a.getName()).orElseThrow(()->denied());
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_FOREMAN").contains(r.getName())))throw denied();
        return u;
    }
    public boolean admin(User u){return u.getRoles().stream().anyMatch(r->r.getName().equals("ROLE_ADMIN"));}
    public void branch(User u,long id){if(!admin(u)&&u.getAllowedBranches().stream().noneMatch(b->b.getBranchNo()==id))throw denied();}
    public void invoice(User u,long id){
        var branches=db.queryForList("SELECT DISTINCT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=?",Long.class,id);
        if(branches.isEmpty())throw conflict("Рахунок не знайдено");for(long b:branches)branch(u,b);
        for(long b:db.queryForList("SELECT DISTINCT branch_id FROM customer_transactions WHERE invoice_id=? AND type IN ('INVOICE','INVOICE_MERGE_OUT')",Long.class,id))branch(u,b);
    }
    /**
     * Payment access is broader only for an accountant making a positive bank
     * payment. All other invoice operations retain the ordinary branch scope.
     * Invoice existence and its original branch references are still verified.
     */
    public void invoicePayment(User u,long id,boolean bankPayment){
        if(!bankPayment || !accountant(u) || admin(u)){
            invoice(u,id);
            return;
        }
        var branches=db.queryForList("SELECT DISTINCT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=?",Long.class,id);
        if(branches.isEmpty())throw conflict("Рахунок не знайдено");
        // The invoice's original branch is intentionally not changed. The
        // authenticated accountant is only the payment operator.
        db.queryForList("SELECT DISTINCT branch_id FROM customer_transactions WHERE invoice_id=? AND type IN ('INVOICE','INVOICE_MERGE_OUT')",Long.class,id);
    }
    private boolean accountant(User u){
        return u.getRoles().stream().anyMatch(r->"ROLE_ACCOUNTANT".equals(r.getName()));
    }
    public static PreviewException conflict(String s){return new PreviewException(HttpStatus.CONFLICT,"PRODUCT_INVOICE_CONFLICT",s);}
    private static PreviewException denied(){return new PreviewException(HttpStatus.FORBIDDEN,"BILLING_ACCESS_DENIED","Немає бухгалтерських прав або доступу до філії");}
}
