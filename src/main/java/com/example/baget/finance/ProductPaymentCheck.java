package com.example.baget.finance;

import com.example.baget.invoices.*;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductPaymentCheck {
    @org.springframework.beans.factory.annotation.Autowired
    private CustomerCreditService credit;
    private final JdbcTemplate db;
    private final InvoiceRepository invoices;
    private final InvoiceFinanceService finance;
    private final EntityManager em;
    private final com.example.baget.payroll.RewardService rewards;
    public ProductPaymentCheck(JdbcTemplate db,InvoiceRepository invoices,InvoiceFinanceService finance,EntityManager em,com.example.baget.payroll.RewardService rewards) {
        this.db=db;this.invoices=invoices;this.finance=finance;this.em=em;this.rewards=rewards;
    }
    private boolean resellerProduct(long product) {
        return "RESELLER".equals(db.queryForObject("SELECT o.commerce_mode FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=?",String.class,product));
    }
    private Long invoiceId(long product) {
        var ids=db.queryForList("SELECT invoice_id FROM product_invoice_claims WHERE product_id=?",Long.class,product);
        if(!ids.isEmpty()) return ids.get(0);
        // Legacy invoices cover the whole order; never use another product's partial invoice.
        var legacy=db.queryForList("SELECT DISTINCT i.id FROM order_products p JOIN invoice_orders io ON io.order_no=p.order_no JOIN invoices i ON i.id=io.invoice_id WHERE p.id=? AND i.lifecycle='ACTIVE' AND i.status NOT IN ('CANCELLED','DRAFT') AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=i.id)",Long.class,product);
        return legacy.size()==1?legacy.get(0):null;
    }
    public Map<String,Object> read(long product) {
        var context=db.queryForMap("SELECT o.CustNo AS customerId,o.BranchNo AS branchNo FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=?",product);
        if(resellerProduct(product)) {
            context.put("canIssue",true);context.put("externalSettlement",true);
            context.put("message","Розрахунки з клієнтом веде фірма-посередник. Її борг підприємству не блокує видачу; перевірте розрахунок із клієнтом у посередника.");
            return context;
        }
        Long id=invoiceId(product);
        context.put("canIssue",false);
        if(id==null){context.put("message","Спочатку бухгалтер має оформити остаточний рахунок за виріб");return context;}
        var invoice=invoices.findById(id).orElseThrow();
        context.put("customerId",invoice.getCustomer().getCustNo());
        context.put("invoiceId",id);context.put("invoiceNo",invoice.getInvoiceNo());
        BigDecimal debt=finance.calculateInvoiceDebt(invoice);
        boolean active=invoice.getLifecycle()==InvoiceEnums.InvoiceLifecycle.ACTIVE && invoice.getStatus()!=InvoiceEnums.InvoiceStatus.CANCELLED && invoice.getStatus()!=InvoiceEnums.InvoiceStatus.DRAFT;
        boolean paid=active && debt.signum()==0;
        var policy=credit.policy(invoice.getCustomer().getCustNo(),((Number)context.get("branchNo")).longValue());
        var terms=credit.terms(id);var due=CustomerCreditService.date(terms.get("dueDate"));
        boolean allowed=CreditRules.canIssue(active,debt,CustomerCreditService.enabled(policy.get("allowed")),due);
        context.put("debt",debt);context.put("canIssue",allowed);context.put("dueDate",due);
        context.put("deferred",allowed && !paid);context.put("overdue",CreditRules.overdue(debt,due,CustomerCreditService.today()));
        context.put("message",paid?"Рахунок оплачено. Видача дозволена":allowed?"Дозволена видача з відстрочкою. Борг залишається до сплати":active?"Для видачі погасіть борг або погодьте дозвіл на відстрочку та дату оплати":"Рахунок нечинний. Зверніться до бухгалтера");
        return context;
    }
    // Match settlement/merge lock order: customer, invoice, then order/product in WarehouseService.
    public Long lock(long product) {
        if(resellerProduct(product))return null;
        Long id=invoiceId(product);
        if(id==null)throw conflict("Спочатку оформіть остаточний рахунок за виріб");
        var customer=db.queryForObject("SELECT customer_id FROM invoices WHERE id=?",Long.class,id);
        db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
        db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,id);
        return id;
    }
    public void requirePaid(long product,Long lockedInvoice) { checkIssue(product,lockedInvoice); }
    public Map<String,Object> checkIssue(long product,Long lockedInvoice) {
        if(resellerProduct(product))return Map.of();
        if(!Objects.equals(lockedInvoice,invoiceId(product)))throw conflict("Рахунок змінився. Оновіть картку виробу");
        var invoice=invoices.findById(lockedInvoice).orElseThrow();em.refresh(invoice);
        if(invoice.getLifecycle()!=InvoiceEnums.InvoiceLifecycle.ACTIVE || invoice.getStatus()==InvoiceEnums.InvoiceStatus.CANCELLED || invoice.getStatus()==InvoiceEnums.InvoiceStatus.DRAFT)
            throw conflict("Рахунок нечинний");
        BigDecimal debt=finance.calculateInvoiceDebt(invoice);
        if(debt.signum()==0)return Map.of();
        Long branch=db.queryForObject("SELECT o.BranchNo FROM order_products p JOIN orders o ON o.OrderNo=p.order_no WHERE p.id=?",Long.class,product);
        var policy=credit.policy(invoice.getCustomer().getCustNo(),branch);var terms=credit.terms(lockedInvoice);
        var due=CustomerCreditService.date(terms.get("dueDate"));
        if(!CreditRules.canIssue(true,debt,CustomerCreditService.enabled(policy.get("allowed")),due))throw conflict("Видача з боргом потребує дозволу бухгалтера та дати оплати рахунку");
        return Map.of("invoice",lockedInvoice,"customer",invoice.getCustomer().getCustNo(),"branch",branch,"debt",debt,"due",due,"policyRevision",policy.get("revision"),"termsRevision",terms.get("revision"));
    }
    public void recordIssue(long product,long revision,boolean accept,String actor,Map<String,Object> snapshot) {
        if(accept)rewards.accrue(product,actor);
        if(snapshot.isEmpty())return;
        db.update("INSERT INTO product_credit_issues(product_id,invoice_id,customer_id,branch_no,tracking_revision,action,debt,due_date,policy_revision,terms_revision,actor) VALUES(?,?,?,?,?,?,?,?,?,?,?)",product,snapshot.get("invoice"),snapshot.get("customer"),snapshot.get("branch"),revision,accept?"ACCEPT":"SEND",snapshot.get("debt"),snapshot.get("due"),snapshot.get("policyRevision"),snapshot.get("termsRevision"),actor);
    }
    private static PreviewException conflict(String m){return new PreviewException(HttpStatus.CONFLICT,"PRODUCT_PAYMENT_REQUIRED",m);}
}
