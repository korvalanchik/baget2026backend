package com.example.baget.finance;

import com.example.baget.customer.*;
import com.example.baget.invoices.*;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class CustomerPayments {
    private final CustomerPaymentAccess access;
    private final CustomerPaymentService finances;
    private final InvoiceSettlementService settlement;
    private final InvoiceRepository invoices;
    private final InvoiceFinanceService invoiceFinance;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CustomerPayments(CustomerPaymentAccess access,CustomerPaymentService finances,InvoiceSettlementService settlement,InvoiceRepository invoices,InvoiceFinanceService invoiceFinance,JdbcTemplate db,ObjectMapper json) {
        this.access=access;this.finances=finances;this.settlement=settlement;this.invoices=invoices;this.invoiceFinance=invoiceFinance;this.db=db;this.json=json;
    }
    public Map<String,Object> orderContext(long order,Authentication a) {
        var c=db.queryForMap("SELECT CustNo AS customerId,BranchNo AS branchNo FROM orders WHERE OrderNo=?",order);
        access.customer(access.user(a),((Number)c.get("customerId")).longValue(),((Number)c.get("branchNo")).longValue());
        return c;
    }
    public CustomerFinanceDTO read(long customer,long branch,Authentication a) {
        access.customer(access.user(a),customer,branch);
        return finances.getCustomerFinance(customer);
    }
    public record Payment(@NotBlank @Size(max=36) String requestKey,@Positive long branchNo,@Positive Long invoiceId,
        @NotNull @DecimalMin("0") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal amount,
        @NotNull @DecimalMin("0") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal allocationAmount,
        @Size(max=2000) String note,@Positive Long cashAccountId) {
        public Payment(String requestKey,long branchNo,Long invoiceId,BigDecimal amount,BigDecimal allocationAmount,String note){this(requestKey,branchNo,invoiceId,amount,allocationAmount,note,null);}
    }
    public com.fasterxml.jackson.databind.JsonNode pay(long customer,Payment r,Authentication a) {
        var u=access.user(a);access.customer(u,customer,r.branchNo());
        try { if(!UUID.fromString(r.requestKey()).toString().equals(r.requestKey())) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw conflict("Некоректний ключ платежу"); }
        db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,customer);
        var prior=db.queryForList("SELECT customer_id,branch_no,actor,payload,result FROM customer_payment_requests WHERE request_key=?",r.requestKey());
        if(!prior.isEmpty()) {
            var p=prior.get(0);
            if(((Number)p.get("customer_id")).longValue()!=customer || ((Number)p.get("branch_no")).longValue()!=r.branchNo() || !a.getName().equals(p.get("actor")) || !same(r,parsePayment(p.get("payload").toString())))
                throw conflict("Цей ключ уже використано для іншого платежу");
            return tree(p.get("result").toString());
        }
        if(r.invoiceId()!=null) {
            access.invoice(u,r.invoiceId());
            db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,r.invoiceId());
            var invoice=invoices.findById(r.invoiceId()).orElseThrow(()->conflict("Рахунок не знайдено"));
            if(!invoice.getCustomer().getCustNo().equals(customer)) throw conflict("Відкрийте фінанси клієнта, на якого оформлений рахунок");
            if(r.amount().add(r.allocationAmount()).compareTo(invoiceFinance.calculateInvoiceDebt(invoice))>0)
                throw conflict("Сума перевищує поточний борг. Оновіть дані; надлишок можна внести окремим авансом");
        } else if(r.allocationAmount().signum()!=0) throw conflict("Не можна використати аванс без рахунку");
        var request=new InvoicePaymentRequest(r.branchNo(),r.invoiceId(),customer,r.amount(),r.allocationAmount(),r.note(),r.cashAccountId());
        var result=r.invoiceId()==null?settlement.settleAdvance(request,a):settlement.settleInvoice(request,a);
        db.update("INSERT INTO customer_payment_requests(request_key,customer_id,branch_no,actor,payload,result) VALUES(?,?,?,?,?,?)",r.requestKey(),customer,r.branchNo(),a.getName(),encode(r),encode(result));
        return tree(encode(result));
    }
    private Payment parsePayment(String v) { try{return json.readValue(v,Payment.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);} }
    static boolean same(Payment a,Payment b) {
        return a.requestKey().equals(b.requestKey()) && a.branchNo()==b.branchNo() && Objects.equals(a.invoiceId(),b.invoiceId())
            && a.amount().compareTo(b.amount())==0 && a.allocationAmount().compareTo(b.allocationAmount())==0 && Objects.equals(a.note(),b.note()) && Objects.equals(a.cashAccountId(),b.cashAccountId());
    }
    private String encode(Object v) { try{return json.writeValueAsString(v);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);} }
    private com.fasterxml.jackson.databind.JsonNode tree(String v) { try{return json.readTree(v);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);} }
    private static PreviewException conflict(String m) { return new PreviewException(HttpStatus.CONFLICT,"CUSTOMER_PAYMENT_CONFLICT",m); }
}
