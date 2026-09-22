package com.example.baget.buh;

import com.example.baget.customer.Customer;
import com.example.baget.customer.CustomerRepository;
import com.example.baget.orders.OrderPaySummaryDTO;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.util.TransactionException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final OrdersRepository ordersRepository;
    private final CustomerRepository customerRepository;
    private final com.example.baget.orderproduct.LegacyOrderGuard legacyGuard;


    @Transactional
    public Transaction processTransaction(Transaction txInput) {

        Customer customer = txInput.getCustomer();
        Orders order = txInput.getOrder();
        if(order!=null) legacyGuard.check(order.getOrderNo());
        String typeCode = txInput.getTransactionType().getCode();
        BigDecimal amount = txInput.getAmount();

        // встановлюємо дату транзакції
        if (txInput.getTransactionDate() == null) {
            txInput.setTransactionDate(OffsetDateTime.now());
        }

        // 1️⃣ ВАЛІДАЦІЯ
        if (customer == null) {
            throw new TransactionException("Transaction must have customerId");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Amount must be positive");
        }

        // 2️⃣ ОТРИМАННЯ БАЛАНСУ
        BigDecimal balance = Optional.ofNullable(
                transactionRepository.getCustomerBalance(customer.getCustNo())
        ).orElse(BigDecimal.ZERO);

        // 3️⃣ ЛОГІКА ДЛЯ ВНЕСЕННЯ КОШТІВ (PAYMENT)
        if (typeCode.equals("PAYMENT")) {

            txInput.setNote("Внесення коштів клієнтом");

            // Створюємо транзакцію поповнення
            Transaction incoming = new Transaction();
            incoming.setTransactionDate(txInput.getTransactionDate());
            incoming.setTransactionType(txInput.getTransactionType());
            incoming.setCustomer(customer);
            incoming.setAmount(amount);
            incoming.setStatus("Completed");
            incoming.setReference(txInput.getReference());
            incoming.setNote("Поповнення балансу клієнта №" + customer.getCustNo());

            transactionRepository.save(incoming);

            // оновлюємо баланс
            balance = balance.add(amount);

            if (order == null) {
                return incoming;
            }

            // 4️⃣ ОПЛАТА ЗАМОВЛЕННЯ ІЗ БАЛАНСУ
            BigDecimal orderDue = Optional.ofNullable(order.getAmountDueN()).orElse(BigDecimal.ZERO);
            BigDecimal orderPaid = Optional.ofNullable(order.getAmountPaid()).orElse(BigDecimal.ZERO);
            BigDecimal orderIncome = Optional.ofNullable(order.getIncome()).orElse(BigDecimal.ZERO);

            if (orderDue.compareTo(BigDecimal.ZERO) <= 0) {
                Transaction noop = new Transaction();
                noop.setTransactionDate(txInput.getTransactionDate());
                noop.setTransactionType(txInput.getTransactionType());
                noop.setCustomer(customer);
                noop.setOrder(order);
                noop.setAmount(BigDecimal.ZERO);
                noop.setStatus("Completed");
                noop.setNote("Замовлення вже оплачено. Кошти додано на баланс клієнта №" + customer.getCustNo());
                return transactionRepository.save(noop);
            }

            // сума, яку реально можна списати
            BigDecimal toPay = balance.min(orderDue);

            Transaction deduction = new Transaction();
            deduction.setTransactionDate(txInput.getTransactionDate());
            deduction.setTransactionType(transactionTypeRepository.findByCode("ORDER_PAYMENT")
                    .orElseThrow(() -> new IllegalArgumentException("Missing ORDER_PAYMENT type")));
            deduction.setCustomer(customer);
            deduction.setOrder(order);
            deduction.setAmount(toPay.negate()); // списання = мінус
            deduction.setStatus("Completed");
            deduction.setNote("Списання з балансу клієнта №" + customer.getCustNo() +
                    " на оплату замовлення №" + order.getOrderNo());

            transactionRepository.save(deduction);

            // оновлюємо баланс після списання
            balance = balance.subtract(toPay);

            // 5️⃣ ОНОВЛЕННЯ ЗАМОВЛЕННЯ
            order.setAmountPaid(orderPaid.add(toPay));
            order.setAmountDueN(orderDue.subtract(toPay));
            order.setIncome(orderIncome.add(toPay));

            updateOrderStatusFromPayments(order);
            ordersRepository.save(order);

            return deduction;
        }

        // INVOICE
        if(typeCode.equals("INVOICE")) {
            if (order == null) throw new TransactionException("Не встановлено № замовлення для інвойсу.");

            Transaction invoiceTx = new Transaction();
            invoiceTx.setTransactionDate(txInput.getTransactionDate());
            invoiceTx.setTransactionType(txInput.getTransactionType());
            invoiceTx.setCustomer(customer);
            invoiceTx.setOrder(order);
            invoiceTx.setAmount(BigDecimal.ZERO);
            invoiceTx.setStatus("Issued");
            invoiceTx.setReference(txInput.getReference());
            invoiceTx.setNote("Виставлено рахунок на замовлення №" + order.getOrderNo());

            return transactionRepository.save(invoiceTx);
        }

        // REFUND
        if(typeCode.equals("REFUND")) {
            if (order != null) {
                BigDecimal paid = Optional.ofNullable(order.getAmountPaid()).orElse(BigDecimal.ZERO);

                if (amount.compareTo(paid) > 0) {
                    throw new TransactionException("Неможливо повернути більше, ніж сплачено за замовлення.");
                }

                Transaction refundTx = new Transaction();
                refundTx.setTransactionDate(txInput.getTransactionDate());
                refundTx.setTransactionType(txInput.getTransactionType());
                refundTx.setCustomer(customer);
                refundTx.setOrder(order);
                refundTx.setAmount(amount.negate());
                refundTx.setStatus("Completed");
                refundTx.setReference(txInput.getReference());
                refundTx.setNote("Повернення коштів за замовлення №" + order.getOrderNo());
                transactionRepository.save(refundTx);

                // Оновлюємо замовлення
                order.setAmountPaid(paid.subtract(amount));
                order.setAmountDueN(Optional.ofNullable(order.getAmountDueN()).orElse(BigDecimal.ZERO).add(amount));
                order.setIncome(Optional.ofNullable(order.getIncome()).orElse(BigDecimal.ZERO).subtract(amount));
                updateOrderStatusFromPayments(order);
                ordersRepository.save(order);
                return refundTx;

            } else {
                if (amount.compareTo(balance) > 0) {
                    throw new TransactionException("Повернення перевищує баланс клієнта.");
                }

                Transaction refundTx = new Transaction();
                refundTx.setTransactionDate(txInput.getTransactionDate());
                refundTx.setTransactionType(txInput.getTransactionType());
                refundTx.setCustomer(customer);
                refundTx.setAmount(amount.negate());
                refundTx.setStatus("Completed");
                refundTx.setReference(txInput.getReference());
                refundTx.setNote("Повернення коштів клієнту");
                return transactionRepository.save(refundTx);
            }
        }

        // DISCOUNT
        if(typeCode.equals("DISCOUNT")) {
            if (order == null) throw new TransactionException("Не встановлено № замовлення для дисконту.");

            BigDecimal due = Optional.ofNullable(order.getAmountDueN()).orElse(BigDecimal.ZERO);
            if (amount.compareTo(due) > 0) throw new TransactionException("Сума дисконту перевищує залишок до оплати.");

            order.setAmountDueN(due.subtract(amount));

            Transaction discountTx = new Transaction();
            discountTx.setTransactionDate(txInput.getTransactionDate());
            discountTx.setTransactionType(txInput.getTransactionType());
            discountTx.setCustomer(customer);
            discountTx.setOrder(order);
            discountTx.setAmount(amount.negate());
            discountTx.setStatus("Completed");
            discountTx.setReference(txInput.getReference());
            discountTx.setNote("Дисконт до замовлення №" + order.getOrderNo());
            ordersRepository.save(order);

            return transactionRepository.save(discountTx);
        }

        // CANCEL
        if(typeCode.equals("CANCEL")) {
            if (order == null) throw new TransactionException("Не встановлено № замовлення для відміни.");

            BigDecimal due = Optional.ofNullable(order.getAmountDueN()).orElse(BigDecimal.ZERO);
            BigDecimal paid = Optional.ofNullable(order.getAmountPaid()).orElse(BigDecimal.ZERO);

            if (amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                    .compareTo(due.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)) != 0) {
                throw new TransactionException("Сума повинна збігатися з залишком авансу (" + due + ")");
            }

            order.setAmountDueN(BigDecimal.ZERO);
            order.setAmountPaid(BigDecimal.ZERO);
            if (order.getCalculationRevision()>0) throw new IllegalStateException("Скасуйте замовлення через нову форму, щоб звільнити резерв");
            order.setStatusOrder(5); // відміна
            updateOrderStatusFromPayments(order);
            ordersRepository.save(order);

            Transaction cancelTx = new Transaction();
            cancelTx.setTransactionDate(txInput.getTransactionDate());
            cancelTx.setTransactionType(txInput.getTransactionType());
            cancelTx.setCustomer(customer);
            cancelTx.setOrder(order);
            cancelTx.setAmount(due);
            cancelTx.setStatus("Completed");
            cancelTx.setReference(txInput.getReference());
            cancelTx.setNote("Списано як дохід при відмові від замовлення №" + order.getOrderNo());
            return transactionRepository.save(cancelTx);
        }

        throw new TransactionException("Unsupported transaction type: " + typeCode);
    }


    private void updateOrderStatusFromPayments(Orders order) {

        BigDecimal paid = transactionRepository.getOrderPaymentsSum(order.getOrderNo());
        BigDecimal total = order.getAmountPaid();

        if (paid.compareTo(total) >= 0) {  // paid >= total
            order.setStatusOrder(4); // Оплачено
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) { // paid > 0
            order.setStatusOrder(9); // Частково оплачено
        } else {
            order.setStatusOrder(7); // До оплати
        }
    }


    @Transactional
    public TransactionDTO createTransaction(TransactionDTO dto) {
        Transaction transaction = toEntity(dto);

        transaction.setTransactionType(
                transactionTypeRepository.findById(dto.getTransactionTypeId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + dto.getTransactionTypeId()))
        );

        transaction.setCustomer(
                customerRepository.findById(dto.getCustomerId())
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + dto.getCustomerId()))
        );

        if (dto.getOrderNo() != null) {
            Orders order = ordersRepository.findById(dto.getOrderNo())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + dto.getOrderNo()));
            transaction.setOrder(order);
        }

        // --- універсальна обробка ---
        Transaction saved = processTransaction(transaction);

        return toDTO(saved);
    }


    @Transactional
    public List<TransactionDTO> createInvoiceTransactions(Long invoiceNo, BigDecimal amount) {
        List<Orders> orders = ordersRepository.findByRahFacNo(invoiceNo);
        if (orders.isEmpty()) {
            throw new EntityNotFoundException("Invoice " + invoiceNo + " not found");
        }

        Set<Long> customerNos = orders.stream()
                .map(o -> o.getCustomer().getCustNo())
                .collect(Collectors.toSet());

        BigDecimal totalDue = orders.stream()
                .map(Orders::getAmountDueN)        // отримуємо BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);  // сумуємо всі значення

        if (customerNos.size() > 1 && amount.compareTo(totalDue) > 0) {
            throw new IllegalArgumentException("Оплата перевищує суму інвойсу для кількох клієнтів");
        }

        List<TransactionDTO> results = new ArrayList<>();
        BigDecimal remaining = amount;

        Long paymentTypeId = transactionTypeRepository.findByCode("PAYMENT")
                .orElseThrow(() -> new IllegalArgumentException("Missing PAYMENT type"))
                .getTypeId();
        Long advanceTypeId = transactionTypeRepository.findByCode("ADVANCE_PAYMENT")
                .orElseThrow(() -> new IllegalArgumentException("Missing ADVANCE_PAYMENT type"))
                .getTypeId();


        for (Orders order : orders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal due = order.getAmountDueN();
            if (due.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal payment = remaining.min(due); // вибираємо менше з remaining і due

                TransactionDTO dto = new TransactionDTO();
                dto.setTransactionTypeId(paymentTypeId);
                dto.setOrderNo(order.getOrderNo());
                dto.setCustomerId(order.getCustomer().getCustNo());
                dto.setAmount(payment);
                dto.setNote("Оплата замовлення " + order.getOrderNo() + " за інвойсом " + invoiceNo);
                dto.setTransactionDate(OffsetDateTime.now());

                TransactionDTO txResult = createTransaction(dto);
                results.add(txResult);

                remaining = remaining.subtract(payment); // віднімаємо payment від remaining
            }
        }

        // переплата → аванс
        if (remaining.compareTo(BigDecimal.ZERO) > 0 && customerNos.size() == 1) {
            Long custNo = customerNos.iterator().next();

            TransactionDTO dto = new TransactionDTO();
            dto.setTransactionTypeId(advanceTypeId);
            dto.setCustomerId(custNo);
            dto.setAmount(remaining);
            dto.setNote("Авансовий платіж за інвойсом " + invoiceNo);
            dto.setTransactionDate(OffsetDateTime.now());

            TransactionDTO txResult = createTransaction(dto);
            results.add(txResult);
        }

        return results;
    }


    @Transactional
    public Long createCollectiveInvoice(List<Long> orderNos) {
        orderNos.forEach(legacyGuard::check);

        Long invoiceNo = generateTodayCode();
        while (ordersRepository.existsByRahFacNo(invoiceNo)) {
            invoiceNo++;
        }
        List<Orders> orders = ordersRepository.findAllById(orderNos);
        for (Orders order : orders) {
            order.setStatusOrder(8); // До оплати
            order.setRahFacNo(invoiceNo);
        }
        ordersRepository.saveAll(orders);
        return invoiceNo;
    }

    // Це стара версія отримання коллективного інвойсу
    public TransactionCollectiveInvoiceDTO getCollectiveInvoice(Long invoiceNo) {

        // Отримуємо замовлення
        List<Orders> orders = ordersRepository.findByRahFacNo(invoiceNo);
        if (orders.isEmpty()) {
            throw new EntityNotFoundException("Invoice " + invoiceNo + " not found");
        }

        // Формуємо зведення по замовленнях
        List<OrderPaySummaryDTO> orderSummaries = orders.stream()
                .map(order -> {
                    BigDecimal billed = order.getAmountDueN().add(order.getAmountPaid());
                    BigDecimal paid   = order.getAmountPaid();
                    BigDecimal due    = order.getAmountDueN();
                    return new OrderPaySummaryDTO(order.getOrderNo(), billed, paid, due);
                })
                .toList();

        // Підсумкові значення
        BigDecimal totalBilled = orderSummaries.stream()
                .map(OrderPaySummaryDTO::billed) // повертає BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = orderSummaries.stream()
                .map(OrderPaySummaryDTO::paid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDue = orderSummaries.stream()
                .map(OrderPaySummaryDTO::due)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Баланс по всіх клієнтах цього інвойсу
        BigDecimal totalCustomerBalance = orders.stream()
                .map(o -> o.getCustomer().getCustNo())
                .distinct()
                .map(transactionRepository::getCustomerBalance) // повертає BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TransactionCollectiveInvoiceDTO(
                invoiceNo,
                orderSummaries,
                totalBilled,
                totalPaid,
                totalDue,
                totalCustomerBalance
        );
    }

    public List<TransactionHistoryView> getTransactionsHistoryByInvoice(Long invoiceNo) {
        return transactionRepository.findByOrder_RahFacNoOrderByTransactionDateDesc(invoiceNo);
    }


    @Transactional
    public List<TransactionDTO> createBatchPayments(List<Long> orderNos) {
        orderNos.forEach(legacyGuard::check);

        List<TransactionDTO> createdInvoices = new ArrayList<>();

        Long invoiceNo = generateTodayCode();
        while (ordersRepository.existsByRahFacNo(invoiceNo)) {
            invoiceNo++;
        }
        List<Orders> orders = ordersRepository.findAllById(orderNos);

        List<Transaction> transactionsToSave = new ArrayList<>();
        List<Orders> ordersToSave = new ArrayList<>();

        for (Orders order : orders) {

            Transaction invoice = new Transaction();
            invoice.setOrder(order);
            invoice.setTransactionType(
                    transactionTypeRepository.findById(10L)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown transaction's type"))
            );
            invoice.setCustomer(
                    customerRepository.findById(order.getCustomer().getCustNo())
                            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + order.getCustomer().getCustNo()))
            );
            invoice.setAmount(order.getAmountDueN());
            invoice.setStatus("Completed");
            invoice.setTransactionDate(OffsetDateTime.now());
            invoice.setNote("Рахунок-фактура №" + invoiceNo + " до замовлення №" + order.getOrderNo());

            transactionsToSave.add(invoice);

            order.setStatusOrder(8); // До оплати
            order.setRahFacNo(invoiceNo);
            ordersToSave.add(order);
        }

        ordersRepository.saveAll(ordersToSave);
        transactionRepository.saveAll(transactionsToSave);

        for (Transaction invoice : transactionsToSave) {
            createdInvoices.add(toDTO(invoice));
        }

        return createdInvoices;
    }


    public TransactionInfoDTO getTransactionInfo(Long orderNo) {
        Orders order = ordersRepository.findById(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        BigDecimal balance = transactionRepository.getCustomerBalance(order.getCustomer().getCustNo());

        return new TransactionInfoDTO(
                order.getOrderNo(),
                order.getCustomer().getCustNo(),
                order.getItemsTotal(),
                order.getAmountPaid(),
                order.getAmountDueN(),
                balance
        );
    }


    public List<TransactionDTO> getTransactionsByOrder(Long orderNo) {
        List<Transaction> transactions = transactionRepository.findByOrder_OrderNo(orderNo);

        return transactions.stream()
                .map(this::toDTO)
                .toList();
    }


    public List<TransactionTypeProjection> getAllTransactionTypes() {
        return transactionTypeRepository.findAllProjectedBy();
    }


    public void completeTransaction(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        tx.setStatus("Completed");
        transactionRepository.save(tx);
    }

    public void cancelTransaction(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        tx.setStatus("Canceled");
        transactionRepository.save(tx);
    }


    public TransactionDTO toDTO(Transaction entity) {
        if (entity == null) {
            return null;
        }

        return TransactionDTO.builder()
                .transactionId(entity.getTransactionId())
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustNo() : null)
                .orderNo(entity.getOrder() != null ? entity.getOrder().getOrderNo() : null)
                .transactionTypeId(entity.getTransactionType() != null ? entity.getTransactionType().getTypeId() : null)
                .transactionTypeCode(entity.getTransactionType() != null ? entity.getTransactionType().getCode() : null)
                .transactionTypeDescription(entity.getTransactionType() != null ? entity.getTransactionType().getDescription() : null)
                .transactionDate(entity.getTransactionDate())
                .amount(entity.getAmount())
                .reference(entity.getReference())
                .status(entity.getStatus())
                .note(entity.getNote())
                .build();
    }

    public Transaction toEntity(TransactionDTO dto) {
        if (dto == null) {
            return null;
        }

        return Transaction.builder()
                .transactionId(dto.getTransactionId())
                .transactionDate(dto.getTransactionDate() != null ? dto.getTransactionDate() : OffsetDateTime.now())
                .amount(dto.getAmount())
                .reference(dto.getReference())
                .status(dto.getStatus())
                .note(dto.getNote())
                .build();

    }

    public Long generateTodayCode() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String result = today.format(formatter) + "001";
        return Long.parseLong(result);
    }

}
