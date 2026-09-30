package com.example.baget.finance;

import com.example.baget.customer.AdvanceAllocationService;
import com.example.baget.customer.CustomerTransactionDTO;
import com.example.baget.invoices.InvoicePaymentRequest;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.example.baget.util.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InvoiceSettlementService {
    private final AdvanceAllocationService advanceAllocationService;
    private final PaymentOrchestrator paymentOrchestrator;
    private final InvoiceOperationContextService invoiceOperationContextService;
    private final UsersRepository usersRepository;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired
    private CustomerPaymentAccess customerPaymentAccess;
    private void lockContext(InvoiceOperationContext ctx) {
        db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,ctx.debtor().getCustNo());
        if(ctx.invoice()!=null) {
            db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,ctx.invoice().getId());
            for(Long orderNo:db.queryForList("SELECT order_no FROM invoice_orders WHERE invoice_id=? ORDER BY order_no",Long.class,ctx.invoice().getId()))
                db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,orderNo);
            em.refresh(ctx.invoice());
            if(ctx.invoice().getLifecycle()!=com.example.baget.invoices.InvoiceEnums.InvoiceLifecycle.ACTIVE
                || ctx.invoice().getStatus()==com.example.baget.invoices.InvoiceEnums.InvoiceStatus.CANCELLED
                || ctx.invoice().getStatus()==com.example.baget.invoices.InvoiceEnums.InvoiceStatus.DRAFT)
                throw new TransactionException("Рахунок скасований або об’єднаний");
        }
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<CustomerTransactionDTO> settleInvoice(
            InvoicePaymentRequest request,
            Authentication authentication) {

        if(request.invoiceId()==null) throw new TransactionException("Для оплати оберіть рахунок");
        customerPaymentAccess.user(authentication);
        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        InvoiceOperationContext ctx = invoiceOperationContextService.resolveContext(request, user);
        customerPaymentAccess.invoice(customerPaymentAccess.user(authentication),ctx.invoice().getId());
        validateAmounts(request);
        lockContext(ctx);

        List<CustomerTransactionDTO> result = new ArrayList<>();

        BigDecimal allocationAmount = Optional.ofNullable(request.allocationAmount()).orElse(BigDecimal.ZERO);

        if (allocationAmount.compareTo(BigDecimal.ZERO) > 0) {
            result.add(advanceAllocationService.allocateAdvance(ctx, allocationAmount, request.note()));
        }

        BigDecimal paymentAmount = Optional.ofNullable(request.amount()).orElse(BigDecimal.ZERO);

        if (paymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            result.addAll(paymentOrchestrator.processPayment(ctx, request, user));
        }

        return result;
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<CustomerTransactionDTO> settleAdvance(
            InvoicePaymentRequest request,
            Authentication authentication) {

        if(request.invoiceId()!=null || Optional.ofNullable(request.allocationAmount()).orElse(BigDecimal.ZERO).signum()!=0)
            throw new TransactionException("Аванс зараховується на баланс клієнта без рахунку");
        customerPaymentAccess.user(authentication);
        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        InvoiceOperationContext ctx = invoiceOperationContextService.resolveContext(request, user);
        customerPaymentAccess.branch(customerPaymentAccess.user(authentication),request.branchNo());
        validateAmounts(request);
        lockContext(ctx);

        return new ArrayList<>(paymentOrchestrator.processPayment(ctx, request, user));
    }

    private void validateAmounts(InvoicePaymentRequest r) {
        for(var value:List.of(Optional.ofNullable(r.amount()).orElse(BigDecimal.ZERO),Optional.ofNullable(r.allocationAmount()).orElse(BigDecimal.ZERO)))
            if(value.signum()<0 || value.stripTrailingZeros().scale()>2 || value.compareTo(new BigDecimal("999999999.99"))>0)
                throw new TransactionException("Вкажіть невід'ємну суму до двох знаків після коми");
        if(Optional.ofNullable(r.amount()).orElse(BigDecimal.ZERO).add(Optional.ofNullable(r.allocationAmount()).orElse(BigDecimal.ZERO)).signum()<=0)
            throw new TransactionException("Вкажіть суму платежу або авансу");
    }
}
