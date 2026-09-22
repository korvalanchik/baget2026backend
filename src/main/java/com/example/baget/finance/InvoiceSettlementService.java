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
    private void lockContext(InvoiceOperationContext ctx) {
        db.queryForObject("SELECT CustNo FROM customer WHERE CustNo=? FOR UPDATE",Long.class,ctx.debtor().getCustNo());
        if(ctx.invoice()!=null) {
            db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,ctx.invoice().getId());
            for(Long orderNo:db.queryForList("SELECT order_no FROM invoice_orders WHERE invoice_id=? ORDER BY order_no",Long.class,ctx.invoice().getId()))
                db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,orderNo);
            em.refresh(ctx.invoice());
            if(ctx.invoice().getLifecycle()!=com.example.baget.invoices.InvoiceEnums.InvoiceLifecycle.ACTIVE
                || ctx.invoice().getStatus()==com.example.baget.invoices.InvoiceEnums.InvoiceStatus.CANCELLED)
                throw new TransactionException("Рахунок скасований або об’єднаний");
        }
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<CustomerTransactionDTO> settleInvoice(
            InvoicePaymentRequest request,
            Authentication authentication) {

        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        InvoiceOperationContext ctx = invoiceOperationContextService.resolveContext(request, user);
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

        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        InvoiceOperationContext ctx = invoiceOperationContextService.resolveContext(request, user);
        lockContext(ctx);

        return new ArrayList<>(paymentOrchestrator.processPayment(ctx, request, user));
    }

}
