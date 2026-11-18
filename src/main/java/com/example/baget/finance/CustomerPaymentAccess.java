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
        if(u.getRoles().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_MANAGER","ROLE_SELLER","ROLE_LEVEL2","ROLE_COUNTER","ROLE_ACCOUNTANT","ROLE_BOOKKEEPER","ROLE_CASHIER").contains(r.getName()))) throw denied();
        return u;
    }
    public boolean admin(User u) { return u.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName())); }
    public void branch(User u,long branch) {
        if(!admin(u) && u.getAllowedBranches().stream().noneMatch(b->b.getBranchNo()==branch)) throw denied();
    }
    public void customer(User u,long customer,long branch) {
        branch(u,branch);
        Long count=db.queryForObject("SELECT COUNT(*) FROM customer c WHERE c.CustNo=? AND (EXISTS(SELECT 1 FROM orders o WHERE o.CustNo=c.CustNo AND o.BranchNo=?) OR EXISTS(SELECT 1 FROM invoices i JOIN invoice_orders io ON io.invoice_id=i.id JOIN orders o ON o.OrderNo=io.order_no WHERE (i.customer_id=c.CustNo OR i.payer_id=c.CustNo) AND o.BranchNo=?) OR EXISTS(SELECT 1 FROM customer_transactions ct WHERE ct.customer_id=c.CustNo AND ct.branch_id=? AND ct.active=1) OR EXISTS(SELECT 1 FROM partner_customer_movements pm WHERE pm.customer_id=c.CustNo AND pm.branch_no=?))",Long.class,customer,branch,branch,branch,branch);
        if(count==null || count==0) throw denied();
    }
    public boolean accountant(User u) {
        return u.getRoles().stream().anyMatch(r->"ROLE_ACCOUNTANT".equals(r.getName()));
    }
    /** A reseller invoice is a company receivable, not a payment collected at a reseller branch. */
    public boolean resellerInvoiceForPartner(User u,long partnerId,long invoiceId) {
        return accountant(u) && db.queryForObject(
                "SELECT COUNT(*) FROM invoice_commercial_owner co JOIN invoices i ON i.id=co.invoice_id WHERE co.invoice_id=? AND co.partner_id=? AND i.customer_id=?",
                Long.class,invoiceId,partnerId,partnerId)>0;
    }
    private boolean resellerInvoice(long invoiceId) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM invoice_commercial_owner co JOIN invoices i ON i.id=co.invoice_id WHERE co.invoice_id=? AND co.partner_id=i.customer_id",
                Long.class,invoiceId)>0;
    }
    public boolean hasBranchAccess(User u,long branch) {
        return admin(u) || u.getAllowedBranches().stream().anyMatch(b->b.getBranchNo()==branch);
    }
    public void invoice(User u,long id) {
        invoicePayment(u,id,false);
    }
    /** Cross-branch payment access is limited to accountants: bank for ordinary invoices, central cash or bank for reseller invoices. */
    public void invoicePayment(User u,long id,boolean bankPayment) {
        if(accountant(u) && resellerInvoice(id)) return;
        var branches=db.queryForList("SELECT DISTINCT o.BranchNo FROM invoice_orders io JOIN orders o ON o.OrderNo=io.order_no WHERE io.invoice_id=?",Long.class,id);
        if(branches.isEmpty()) throw denied();
        boolean crossBranchBankPayment=bankPayment && accountant(u);
        for(long b:branches) if(!crossBranchBankPayment) branch(u,b);
        for(long b:db.queryForList("SELECT DISTINCT branch_id FROM customer_transactions WHERE invoice_id=? AND type IN ('INVOICE','INVOICE_MERGE_OUT')",Long.class,id))
            if(!crossBranchBankPayment) branch(u,b);
    }
    private static PreviewException denied() { return new PreviewException(HttpStatus.FORBIDDEN,"CUSTOMER_PAYMENT_ACCESS","Немає права приймати оплату або доступу до клієнта чи філії"); }
}
