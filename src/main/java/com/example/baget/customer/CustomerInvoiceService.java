package com.example.baget.customer;

import com.example.baget.invoices.*;
import com.example.baget.ledger.*;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.orders.OrdersService;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.example.baget.util.InvoiceServiceUtil;
import com.example.baget.util.TransactionException;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class CustomerInvoiceService {

    private final CustomerTransactionRepository customerTxRepository;
    private final OrdersRepository ordersRepository;
    private final UsersRepository usersRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceOrderRepository invoiceOrderRepository;
    private final EntityManager entityManager;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceServiceUtil invoiceServiceUtil;
    private final OrdersService ordersService;
    private final LedgerService ledgerService;
    private final com.example.baget.orderproduct.ProductionService production;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    @Transactional
    public InvoiceDTO issueInvoice(Long orderNo, IssueInvoiceFullRequest request, Authentication authentication) {

        if(request==null || request.getInvoice()==null) throw new TransactionException("Не вказано параметри рахунку");
        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE", Long.class, orderNo);
        // 1️⃣ Отримуємо order
        Orders order = ordersRepository.findById(orderNo)
                .orElseThrow(() -> new TransactionException("Замовлення не знайдено: " + orderNo));

        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new TransactionException("Замовлення не має клієнта");
        }

        // 2️⃣ Перевірка: чи вже є інвойс
        if (invoiceOrderRepository.existsByOrder_OrderNoAndInvoice_Lifecycle(orderNo, InvoiceEnums.InvoiceLifecycle.ACTIVE)) {
            throw new TransactionException("Замовлення вже в активному інвойсі");
        }

        // 3️⃣ Сума
        BigDecimal amount = order.getCalculationRevision()>0 ? production.invoiceAmount(orderNo,authentication) : request.getInvoice().getAmount() != null
                ? request.getInvoice().getAmount()
                : order.getAmountDueN();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Некоректна сума рахунку: " + amount);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 4️⃣ Генеруємо номер інвойсу
        Long invoiceNo = invoiceServiceUtil.generateTodayCode();
        while (invoiceRepository.existsByInvoiceNo(invoiceNo)) {
            invoiceNo++;
        }

        // 5️⃣ Створюємо Invoice
        Invoice invoice = Invoice.builder()
                .invoiceNo(invoiceNo)
                .customer(customer)
                .type(InvoiceEnums.InvoiceType.SIMPLE)
                .status(InvoiceEnums.InvoiceStatus.ISSUED)
                .lifecycle(InvoiceEnums.InvoiceLifecycle.ACTIVE)
                .totalAmount(amount)
                .note(request.getInvoice().getReference())
                .build();

        invoiceRepository.save(invoice);
        entityManager.flush();

        // 6️⃣ Створюємо InvoiceOrder
        InvoiceOrder io = new InvoiceOrder();
        io.setInvoice(invoice);
        io.setOrder(order);
        io.setAmount(amount);

        invoiceOrderRepository.save(io);
        invoice.getInvoiceOrders().add(io);

        CustomerTransactionType type = CustomerTransactionType.INVOICE;

        // 7️⃣ CustomerTransaction (для UI/історії)
        CustomerTransaction tx = saveAndFlush(
             CustomerTransaction.builder()
                .customer(customer)
                .order(order)
                .branch(order.getBranch())
                .invoice(invoice)
                .type(type)
                .amount(amount.negate()) // 🔥 борг = мінус
                .createdAt(now)
                .note("Інвойс №" + invoiceNo)
                .build()
        );

        // 8️⃣ Ledger → борг клієнта
        ledgerService.createEntry(
                new LedgerRequest(
                        order.getBranch(),
                        type.getDirection(),          // 🔥 з enum
                        type.getLedgerCategory(),     // 🔥 з enum
                        amount,
                        now,
                        user,

                        customer.getCustNo(),
                        tx.getId(),
                        null,
                        invoice.getId(),

                        "INV-" + invoiceNo,
                        "Виставлення інвойсу"
                )
        );

        // 9️⃣ Оновлюємо order
        if(order.getCalculationRevision()>0) {
            // Preserve a later physical transfer stage; invoice identity lives in invoice_orders.
            if(order.getStatusOrder()==6) order.setStatusOrder(7);
            order.setRahFacNo(invoiceNo);
            order.setAmountDueN(amount); // Compatibility statistics only.
            ordersRepository.saveAndFlush(order);
            db.update("UPDATE orders SET calculation_revision=calculation_revision+1 WHERE OrderNo=?",orderNo);
        } else ordersService.update(orderNo, request.getOrder());

        return invoiceMapper.toDto(invoice);
    }

    private CustomerTransaction saveAndFlush(CustomerTransaction tx) {
        customerTxRepository.save(tx);
        entityManager.flush();
        return tx;
    }

}

