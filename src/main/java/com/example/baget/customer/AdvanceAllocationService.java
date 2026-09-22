package com.example.baget.customer;

import com.example.baget.finance.InvoiceOperationContext;
import com.example.baget.invoices.InvoiceEnums;
import com.example.baget.invoices.InvoiceFinanceService;
import com.example.baget.invoices.InvoiceOrder;
import com.example.baget.invoices.InvoiceOrderRepository;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.util.TransactionException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvanceAllocationService {

    private final CustomerTransactionRepository customerTxRepository;
    private final InvoiceFinanceService invoiceFinanceService;
    private final InvoiceOrderRepository invoiceOrderRepository;
    private final OrdersRepository ordersRepository;
    private final CustomerTransactionMapper customerTransactionMapper;
    private final com.example.baget.ledger.LedgerService ledgerService;

    @Transactional
    public CustomerTransactionDTO allocateAdvance(
            InvoiceOperationContext ctx,
            BigDecimal requestedAmount,
            String note
    ) {

        if (requestedAmount == null
                || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new TransactionException("Сума алокації повинна бути більше 0");
        }

        Customer customer = ctx.invoice().getCustomer();

        BigDecimal availableAdvance = customerTxRepository.calculateAvailableAdvance(customer.getCustNo());

        if (availableAdvance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("У клієнта відсутній доступний аванс");
        }

        if (requestedAmount.compareTo(availableAdvance) > 0) {
            throw new TransactionException("Сума алокації перевищує доступний аванс");
        }

        BigDecimal invoiceDebt = invoiceFinanceService.calculateInvoiceDebt(ctx.invoice());

        if (invoiceDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Інвойс вже оплачено");
        }

        BigDecimal allocationAmount = requestedAmount.min(invoiceDebt);

        CustomerTransaction tx =
                customerTxRepository.save(
                        CustomerTransaction.builder()
                                .customer(customer)
                                .branch(ctx.branch())
                                .invoice(ctx.invoice())
                                .type(CustomerTransactionType.ADVANCE_ALLOCATION)
                                .amount(allocationAmount.negate())
                                .note(note)
                                .build()
                );

        // ----------------------------
        // SIMPLE invoice → update order
        // ----------------------------
        List<InvoiceOrder> invoiceOrders =
                invoiceOrderRepository.findByInvoice_Id(ctx.invoice().getId());

        if (ctx.invoice().getType() == InvoiceEnums.InvoiceType.SIMPLE) {

            Orders order = invoiceOrders.get(0).getOrder();

            if (order != null) {

                BigDecimal newAmountPaid = java.util.Objects.requireNonNullElse(order.getAmountPaid(), BigDecimal.ZERO).add(allocationAmount);
                BigDecimal newAmountDueN = java.util.Objects.requireNonNullElse(order.getAmountDueN(), BigDecimal.ZERO).subtract(allocationAmount);

                order.setAmountPaid(newAmountPaid);
                order.setAmountDueN(newAmountDueN);
                order.setIncome(java.util.Objects.requireNonNullElse(order.getIncome(), BigDecimal.ZERO).add(allocationAmount));

                if (invoiceDebt.subtract(allocationAmount).compareTo(BigDecimal.ZERO) <= 0) {
                    if(order.getStatusOrder()!=11 && order.getStatusOrder()!=12) order.setStatusOrder(4);
                } else {
                    if(order.getStatusOrder()!=11 && order.getStatusOrder()!=12) order.setStatusOrder(9);
                }

                ordersRepository.save(order);
            }
        }

        if(invoiceFinanceService.usesProductionLedger(ctx.invoice())) {
            customerTxRepository.flush();
            var now=java.time.OffsetDateTime.now();
            ledgerService.createEntry(new com.example.baget.ledger.LedgerRequest(ctx.branch(),
                com.example.baget.ledger.LedgerDirection.OUT,com.example.baget.ledger.LedgerCategory.INTERNAL_TRANSFER_OUT,
                allocationAmount,now,ctx.user(),customer.getCustNo(),tx.getId(),ctx.payer(),null,"ADV-ALLOC-"+tx.getId(),note));
            ledgerService.createEntry(new com.example.baget.ledger.LedgerRequest(ctx.branch(),
                com.example.baget.ledger.LedgerDirection.IN,com.example.baget.ledger.LedgerCategory.INTERNAL_TRANSFER_IN,
                allocationAmount,now,ctx.user(),customer.getCustNo(),tx.getId(),ctx.payer(),ctx.invoice().getId(),"ADV-ALLOC-"+tx.getId(),note));
        }
        invoiceFinanceService.refreshInvoiceStatus(ctx.invoice());

        return customerTransactionMapper.toDTO(tx);
    }
}
