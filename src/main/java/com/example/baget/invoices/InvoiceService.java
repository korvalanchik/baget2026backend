package com.example.baget.invoices;

import com.example.baget.branch.Branch;
import com.example.baget.branch.BranchRepository;
import com.example.baget.customer.*;
import com.example.baget.items.ItemViewDTO;
import com.example.baget.items.Items;
import com.example.baget.ledger.*;
import com.example.baget.orders.OrderPaySummaryDTO;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.parts.Parts;
import com.example.baget.parts.PartsRepository;
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
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final UsersRepository usersRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceOrderRepository invoiceOrderRepository;
    private final LedgerRepository ledgerRepository;
    private final InvoiceMapper invoiceMapper;
    private final EntityManager entityManager;
    private final InvoiceServiceUtil invoiceServiceUtil;
    private final OrdersRepository ordersRepository;
    private final BranchRepository branchRepository;
    private final CustomerTransactionRepository customerTransactionRepository;
    private final LedgerService ledgerService;
    private final PartsRepository partsRepository;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    @Transactional
    public InvoiceDTO mergeInvoices(MergeInvoicesRequest request, Authentication authentication) {

        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new TransactionException("Користувач не знайдений: " + username));

        List<Long> invoiceIds = request.invoiceIds();
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw new TransactionException("Список інвойсів порожній");
        }

        Set<Long> uniqueIds = new HashSet<>(invoiceIds);
        if (uniqueIds.size() != invoiceIds.size()) {
            throw new TransactionException("Список інвойсів містить дублікати");
        }

        for(Long id:invoiceIds) if(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,id)>0)
            throw new TransactionException("Рахунки за виробами об’єднуються в розділі бухгалтера");
        for(Long id:new java.util.TreeSet<>(invoiceIds))
            db.queryForObject("SELECT id FROM invoices WHERE id=? FOR UPDATE",Long.class,id);
        var lockedOrders=new java.util.TreeSet<Long>();
        for(Long id:invoiceIds) lockedOrders.addAll(db.queryForList("SELECT order_no FROM invoice_orders WHERE invoice_id=?",Long.class,id));
        for(Long id:lockedOrders) db.queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,id);
// 1️⃣ Завантажуємо інвойси
        List<Invoice> invoices = invoiceRepository.findAllById(invoiceIds);

        if (invoices.size() != invoiceIds.size()) {
            throw new TransactionException("Деякі інвойси не знайдено");
        }

// 2️⃣ Перевірки
        for (Invoice inv : invoices) {

            if (inv.getLifecycle() != InvoiceEnums.InvoiceLifecycle.ACTIVE) {
                throw new TransactionException("Інвойс " + inv.getInvoiceNo() + " вже не активний");
            }

            if (inv.getStatus() != InvoiceEnums.InvoiceStatus.ISSUED) {
                throw new TransactionException("Можна об'єднувати тільки неоплачені");
            }
        }

// 3️⃣ Визначаємо payer
        Customer payer;

        if (request.payerId() != null) {
            payer = customerRepository.findById(request.payerId())
                    .orElseThrow(() -> new TransactionException("Платника не знайдено"));
        } else {
            Set<Long> customerIds = invoices.stream()
                    .map(i -> i.getCustomer().getCustNo())
                    .collect(Collectors.toSet());

            if (customerIds.size() > 1) {
                throw new TransactionException(
                        "MULTIPLE_CUSTOMERS",
                        "Для рахунку з кількома клієнтами потрібно вибрати платника"
                );
            }

            payer = invoices.get(0).getCustomer();
        }

        OffsetDateTime now = OffsetDateTime.now();

// 4️⃣ Рахуємо борг, який переносимо
        BigDecimal totalDebtToTransfer = BigDecimal.ZERO;

        Map<Long, BigDecimal> invoiceDebts = new HashMap<>();

        for (Invoice inv : invoices) {
            BigDecimal debt = invoiceRepository.calculateInvoiceDebt(inv.getId());

            if (debt.compareTo(BigDecimal.ZERO) < 0) {
                throw new TransactionException("Інвойс " + inv.getInvoiceNo() + " має переплату");
            }

            invoiceDebts.put(inv.getId(), debt);
            totalDebtToTransfer = totalDebtToTransfer.add(debt);
        }

        if (totalDebtToTransfer.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Немає боргу для об'єднання");
        }

// 5️⃣ Генеруємо номер
        Long invoiceNo = invoiceServiceUtil.generateTodayCode();
        while (invoiceRepository.existsByInvoiceNo(invoiceNo)) {
            invoiceNo++;
        }

// 6️⃣ Створюємо новий інвойс
        Invoice newInvoice = Invoice.builder()
                .invoiceNo(invoiceNo)
                .customer(payer)
                .payer(payer)
                .type(InvoiceEnums.InvoiceType.CONSOLIDATED)
                .status(InvoiceEnums.InvoiceStatus.ISSUED)
                .lifecycle(InvoiceEnums.InvoiceLifecycle.ACTIVE)
                .totalAmount(totalDebtToTransfer) // 🔥 ВАЖЛИВО: debt, а не original amount
                .note(request.note())
                .createdAt(now)
                .build();

        invoiceRepository.save(newInvoice);
        entityManager.flush();

// 7️⃣ Копіюємо InvoiceOrder
        List<InvoiceOrder> allInvoiceOrders =
                invoiceOrderRepository.findByInvoice_IdIn(invoiceIds);

        if (allInvoiceOrders.isEmpty()) {
            throw new TransactionException("Інвойси не містять замовлень");
        }

        for (InvoiceOrder oldIo : allInvoiceOrders) {

            InvoiceOrder newIo = new InvoiceOrder();
            newIo.setInvoice(newInvoice);
            newIo.setOrder(oldIo.getOrder());
            newIo.setAmount(oldIo.getAmount());

            invoiceOrderRepository.save(newIo);
        }

// 8️⃣ Ledger: закриваємо старі інвойси (IN)

        Map<Long, Branch> invoiceBranchMap = allInvoiceOrders.stream()
                .collect(Collectors.toMap(
                        io -> io.getInvoice().getId(),
                        io -> io.getOrder().getBranch(),
                        (existing, replacement) -> {
                            if (!existing.equals(replacement)) {
                                throw new TransactionException("Інвойс має кілька філій");
                            }
                            return existing;
                        }
                ));

        Map<Long, Orders> invoiceOrderMap = allInvoiceOrders.stream()
                .collect(Collectors.toMap(
                        io -> io.getInvoice().getId(),
                        InvoiceOrder::getOrder,
                        (existing, replacement) -> existing
                ));

        List<CustomerTransaction> txs = new ArrayList<>();

        for (Invoice old : invoices) {

            BigDecimal debt = invoiceDebts.get(old.getId());

            if (debt.compareTo(BigDecimal.ZERO) <= 0) continue;

            Branch oldBranch = invoiceBranchMap.get(old.getId());

            if (oldBranch == null) {
                throw new TransactionException("Не знайдено branch для інвойсу " + old.getInvoiceNo());
            }

// CustomerTransactions: закриваємо борг по старому інвойсу
            CustomerTransaction tx = customerTransactionRepository.save(
                    CustomerTransaction.builder()
                            .branch(oldBranch)
                            .customer(old.getCustomer())
                            .invoice(old)
                            .order(invoiceOrderMap.get(old.getId()))
                            .type(CustomerTransactionType.INVOICE_MERGE_IN)
                            .amount(debt)
                            .createdAt(now)
                            .note("Перенос боргу в інвойс " + invoiceNo)
                            .build()
            );
            txs.add(tx);

        }

        Branch branch = branchRepository.findByBranchNo(request.branchNo())
                .orElseThrow(() -> new TransactionException("Філію не вказано"));

// CustomerTransactions: відкриваємо борг на новому інвойсі
        CustomerTransaction mergeOutTx = customerTransactionRepository.save(
                CustomerTransaction.builder()
                        .branch(branch)
                        .customer(newInvoice.getCustomer())
                        .invoice(newInvoice)
                        .type(CustomerTransactionType.INVOICE_MERGE_OUT)
                        .amount(totalDebtToTransfer.negate())
                        .createdAt(now)
                        .note("Об'єднання інвойсів: " + invoiceIds)
                        .build()
        );
        txs.add(mergeOutTx);

        for (CustomerTransaction tx : txs) {

            ledgerService.createEntry(
                    new LedgerRequest(
                            tx.getBranch(),
                            tx.getType().getDirection(),          // 🔥 з enum
                            tx.getType().getLedgerCategory(),     // 🔥 з enum
                            tx.getAmount().abs(),                 // 🔥 нормалізація
                            now,
                            user,

                            tx.getCustomer().getCustNo(),
                            tx.getId(),                           // 🔥 зв’язок
                            payer,
                            tx.getInvoice() != null ? tx.getInvoice().getId() : null,

                            buildMergeReference(tx, invoiceNo),
                            tx.getNote()
                    )
            );
        }


// 🔟 Старі інвойси → MERGED
        for (Invoice old : invoices) {
            old.setLifecycle(InvoiceEnums.InvoiceLifecycle.MERGED);
            old.setPayer(payer);
        }

// 1️⃣1️⃣ Оновлюємо orders (UI)
        final Long invoiceNoFinal = invoiceNo;

        List<Orders> ordersToUpdate = new ArrayList<>();

        for (Invoice old : invoices) {

            Orders order = invoiceOrderMap.get(old.getId());
            BigDecimal debt = invoiceDebts.get(old.getId());

            if (order == null) continue;

            if(order.getCalculationRevision()>0) {
                order.setRahFacNo(invoiceNoFinal);
                if(order.getStatusOrder()!=11 && order.getStatusOrder()!=12) order.setStatusOrder(8);
                ordersToUpdate.add(order);
                continue;
            }
            // Legacy statistics.
            order.setAmountPaid(order.getAmountPaid().add(debt));
            order.setAmountDueN(order.getAmountDueN().subtract(debt));
            order.setIncome(order.getIncome().add(debt));
            order.setRahFacNo(invoiceNoFinal);
            order.setStatusOrder(4);

            ordersToUpdate.add(order);
        }
        ordersRepository.saveAll(ordersToUpdate);

        return invoiceMapper.toDto(newInvoice);

    }

    private String buildMergeReference(CustomerTransaction tx, Long newInvoiceNo) {

        if (tx.getType() == CustomerTransactionType.INVOICE_MERGE_IN) {
            return "MERGE->INV-" + newInvoiceNo;
        }

        return "MERGE-OUT->INV-" + newInvoiceNo;
    }


    @Transactional
    public InvoiceViewDTO getInvoice(Long invoiceId) {
        if(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,invoiceId)>0)
            throw new TransactionException("Склад цього рахунку доступний у розділі бухгалтера: рахунки за виробами");

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() ->
                        new TransactionException("Інвойс не знайдено"));

        Set<Long> partNos = invoice.getInvoiceOrders()
                .stream()
                .flatMap(io -> io.getOrder().getItems().stream())
                .map(Items::getPartNo)
                .collect(Collectors.toSet());

        Map<Long, String> descriptions =
                partsRepository.findAllById(partNos)
                        .stream()
                        .collect(Collectors.toMap(
                                Parts::getPartNo,
                                Parts::getDescription
                        ));


        InvoiceViewDTO dto = new InvoiceViewDTO();

        dto.setInvoiceId(invoice.getId());
        dto.setInvoiceNo(invoice.getInvoiceNo());

        dto.setCustomerName(
                invoice.getCustomer().getCompany());

        dto.setType(invoice.getType());
        dto.setStatus(invoice.getStatus());

        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setCreatedAt(invoice.getCreatedAt());

        List<InvoiceOrderViewDTO> orders =
                invoice.getInvoiceOrders()
                        .stream()
                        .map(io -> mapOrder(io, descriptions))
                        .toList();

        dto.setOrders(orders);

        return dto;
    }

    private InvoiceOrderViewDTO mapOrder(
            InvoiceOrder invoiceOrder,
            Map<Long, String> descriptions) {

        Orders order = invoiceOrder.getOrder();

        InvoiceOrderViewDTO dto =
                new InvoiceOrderViewDTO();

        dto.setOrderNo(order.getOrderNo());
        dto.setAmount(invoiceOrder.getAmount());

        dto.setBranchName(
                order.getBranch().getName());

        dto.setSaleDate(order.getSaleDate());
        dto.setShipDate(order.getShipDate());

        dto.setItems(
                order.getItems()
                        .stream()
                        .map(item -> mapItem(item, descriptions))
                        .toList()
        );

        return dto;
    }

    private ItemViewDTO mapItem(Items item, Map<Long, String> descriptions) {

        ItemViewDTO dto = new ItemViewDTO();

        dto.setPartNo(item.getPartNo());

        dto.setDescription(
                descriptions.getOrDefault(
                        item.getPartNo(),
                        ""
                )
        );

        dto.setWidth(item.getWidth());
        dto.setHeight(item.getHeight());

        dto.setQuantity(item.getQuantity());
        BigDecimal billedQuantity = item.getActualConsumption()!=null ? item.getActualConsumption() : item.getPlannedConsumption();
        dto.setQty(billedQuantity!=null ? billedQuantity.doubleValue() : item.getQty());

        dto.setSellPrice(
                BigDecimal.valueOf(
                        Optional.ofNullable(item.getSellPrice())
                                .orElse(0d)));

        dto.setDiscount(
                BigDecimal.valueOf(
                        Optional.ofNullable(item.getDiscount())
                                .orElse(0d)));

        BigDecimal qty =
                BigDecimal.valueOf(
                        Optional.ofNullable(item.getQty())
                                .orElse(0d));

        BigDecimal price =
                dto.getSellPrice();

        BigDecimal discountPercent =
                dto.getDiscount();

        BigDecimal rowTotal = qty
                .multiply(price)
                .multiply(
                        BigDecimal.valueOf(100)
                                .subtract(discountPercent)
                                .divide(
                                        BigDecimal.valueOf(100),
                                        4,
                                        RoundingMode.HALF_UP
                                )
                )
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal snapshotAmount = item.getActualAmount()!=null ? item.getActualAmount() : item.getCalculatedAmount();
        dto.setTotal(snapshotAmount!=null ? snapshotAmount : rowTotal);

        return dto;
    }

    public CollectiveInvoiceDTO getCollectiveInvoice(Long invoiceNo) {

        // 1️⃣ Invoice
        Invoice invoice = invoiceRepository
                .findByInvoiceNoAndLifecycle(invoiceNo, InvoiceEnums.InvoiceLifecycle.ACTIVE)
                .orElseThrow(() -> new TransactionException("Invoice " + invoiceNo + " not found"));

        if(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE invoice_id=?",Long.class,invoice.getId())>0)
            throw new TransactionException("Розрахунки за виробами доступні в розділі бухгалтера");
        // 2️⃣ InvoiceOrders
        List<InvoiceOrder> invoiceOrders =
                invoiceOrderRepository.findByInvoice_Id(invoice.getId());

        if (invoiceOrders.isEmpty()) {
            throw new IllegalStateException("Invoice has no orders");
        }

        // 3️⃣ Збираємо orderIds
        List<Long> orderIds = invoiceOrders.stream()
                .map(io -> io.getOrder().getOrderNo())
                .toList();

        // 4️⃣ 🔥 ОДИН SQL ДО LEDGER
        List<Object[]> ledgerData = ledgerRepository.sumInOutByOrders(orderIds);

        // 5️⃣ Перетворюємо в Map для швидкого доступу
        Map<Long, BigDecimal> inMap = new HashMap<>();
        Map<Long, BigDecimal> outMap = new HashMap<>();

        for (Object[] row : ledgerData) {
            Long orderId = (Long) row[0];
            BigDecimal in = (BigDecimal) row[1];
            BigDecimal out = (BigDecimal) row[2];

            inMap.put(orderId, in);
            outMap.put(orderId, out);
        }

        // 6️⃣ Формуємо summaries
        List<OrderPaySummaryDTO> orderSummaries = invoiceOrders.stream()
                .map(io -> {

                    Long orderId = io.getOrder().getOrderNo();

                    BigDecimal billed = io.getAmount();

                    BigDecimal in = inMap.getOrDefault(orderId, BigDecimal.ZERO);
                    BigDecimal out = outMap.getOrDefault(orderId, BigDecimal.ZERO);

                    BigDecimal due = out.subtract(in);

                    return new OrderPaySummaryDTO(orderId, billed, in, due);
                })
                .toList();

        // 7️⃣ totals
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDue = BigDecimal.ZERO;

        for (OrderPaySummaryDTO o : orderSummaries) {
            totalBilled = totalBilled.add(o.billed());
            totalPaid   = totalPaid.add(o.paid());
            totalDue    = totalDue.add(o.due());
        }

        // 8️⃣ баланс платника (тільки payer!)
        BigDecimal totalCustomerBalance =
                ledgerRepository.getCustomerBalance(invoice.getCustomer().getCustNo());

        return new CollectiveInvoiceDTO(
                invoiceNo,
                orderSummaries,
                totalBilled,
                totalPaid,
                totalDue,
                totalCustomerBalance
        );
    }


}
