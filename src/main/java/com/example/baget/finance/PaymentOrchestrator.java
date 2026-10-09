package com.example.baget.finance;

import com.example.baget.customer.CustomerTransaction;
import com.example.baget.customer.CustomerTransactionDTO;
import com.example.baget.customer.CustomerTransactionMapper;
import com.example.baget.customer.CustomerTransactionType;
import com.example.baget.invoices.InvoicePaymentRequest;
import com.example.baget.ledger.LedgerCategory;
import com.example.baget.ledger.LedgerDirection;
import com.example.baget.ledger.LedgerRequest;
import com.example.baget.ledger.LedgerService;
import com.example.baget.users.User;
import com.example.baget.util.TransactionException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.baget.cash.CashService cash;
    private final List<PaymentProcessor> processors;
    private final LedgerService ledgerService;
    private final CustomerTransactionMapper customerTransactionMapper;
    private final com.example.baget.invoices.InvoiceFinanceService invoiceFinanceService;

    @Transactional
    public List<CustomerTransactionDTO> processPayment(
            InvoiceOperationContext ctx,
            InvoicePaymentRequest request,
            User user) {

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Сума оплати має бути більше 0");
        }

        long cashAccountId=cash.paymentAccount(user,ctx.branch().getBranchNo(),request.cashAccountId());
        OffsetDateTime now = OffsetDateTime.now();

        List<PaymentProcessor> matchedProcessors  = processors.stream()
                .filter(p -> p.supports(request))
                .toList();

        if (matchedProcessors .size() != 1) {
            throw new IllegalStateException("Expected exactly 1 processor, got " + matchedProcessors .size());
        }

        PaymentProcessor processor = matchedProcessors.get(0);

        List<CustomerTransaction> txs = processor.process(ctx, request, now);

        // 🔥 Ledger через один API
        for (CustomerTransaction tx : txs) {
            ledgerService.createEntry(buildLedgerRequest(ctx, tx, user, now, request.note()),cashAccountId);
        }

        if(ctx.invoice()!=null) invoiceFinanceService.refreshInvoiceStatus(ctx.invoice());
        return txs.stream().map(customerTransactionMapper::toDTO).toList();
    }

    /** Invoice processors create UI transactions; only this method posts their financial entries. */
    @Transactional
    public void postInvoiceTransactions(List<CustomerTransaction> transactions, User user, com.example.baget.customer.Customer payer) {
        for(var tx:transactions) {
            if(!java.util.Set.of(CustomerTransactionType.INVOICE,CustomerTransactionType.INVOICE_MERGE_IN,CustomerTransactionType.INVOICE_MERGE_OUT).contains(tx.getType())
                || tx.getInvoice()==null || tx.getId()==null || tx.getAmount()==null || tx.getAmount().signum()==0)
                throw new IllegalArgumentException("Invalid invoice posting");
            ledgerService.createEntry(new LedgerRequest(tx.getBranch(),tx.getType().getDirection(),tx.getType().getLedgerCategory(),
                tx.getAmount().abs(),tx.getCreatedAt(),user,tx.getCustomer().getCustNo(),tx.getId(),payer,tx.getInvoice().getId(),
                "INV-"+tx.getInvoice().getInvoiceNo(),tx.getNote()));
        }
    }

    private LedgerRequest buildLedgerRequest(
            InvoiceOperationContext ctx,
            CustomerTransaction tx,
            User user,
            OffsetDateTime now,
            String note) {

        LedgerCategory category =
                tx.getType().getLedgerCategory();

        return new LedgerRequest(
                ctx.branch(),
                LedgerDirection.IN,
                category,
                tx.getAmount(),
                now,
                user,

                ctx.debtor().getCustNo(),
                tx.getId(),
                ctx.payer(),
                tx.getType()!=CustomerTransactionType.ADVANCE && ctx.invoice()!=null ? ctx.invoice().getId() : null,

                buildReference(ctx, tx),
                note
        );
    }


    private String buildReference(InvoiceOperationContext ctx, CustomerTransaction tx) {

        if (tx.getType() == CustomerTransactionType.ADVANCE) {
            return "ADV-" + ctx.debtor().getCustNo();
        }

        return "PAY-" + ctx.invoice().getInvoiceNo();
    }

}