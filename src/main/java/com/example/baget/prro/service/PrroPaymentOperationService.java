package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.service.PrroOperationService.Operation;
import com.example.baget.prro.service.PrroOperationService.PrepareOperation;
import com.example.baget.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Connects one saved customer payment request to at most one PRRO operation.
 * It only prepares local state; it does not construct a receipt or contact DPS.
 */
@Service
public class PrroPaymentOperationService {
    private final JdbcTemplate db;
    private final PrroAccessService access;
    private final PrroOperationService operations;
    private final ObjectMapper json;

    public PrroPaymentOperationService(JdbcTemplate db, PrroAccessService access,
                                       PrroOperationService operations, ObjectMapper json) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
        this.operations = Objects.requireNonNull(operations);
        this.json = Objects.requireNonNull(json);
    }

    @Transactional
    public PaymentOperationView prepare(String paymentRequestKey, long branchNo,
                                        org.springframework.security.core.Authentication authentication) {
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);
        Payment payment = lockEligiblePayment(paymentRequestKey, branchNo);
        access.requireCustomerInBranch(payment.customerId(), payment.branchNo());

        List<Link> links = db.query("""
                SELECT operation_id, branch_no, cash_account_id
                  FROM prro_payment_operation_links
                 WHERE payment_request_key = ?
                """, (rs, rowNum) -> new Link(rs.getLong("operation_id"),
                rs.getLong("branch_no"), rs.getLong("cash_account_id")), paymentRequestKey);
        if (!links.isEmpty()) {
            Link link = links.get(0);
            if (link.branchNo() != branchNo || link.cashAccountId() != payment.cashAccountId()) {
                throw conflict("Платіж уже пов'язаний з іншою філією або касою");
            }
            return view(paymentRequestKey, operations.get(link.operationId()), true);
        }

        List<Device> devices = db.query("""
                SELECT id, active_shift_key, active_signer_key_id
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(rs.getLong("id"),
                rs.getString("active_shift_key"), rs.getString("active_signer_key_id")), branchNo);
        if (devices.isEmpty()) {
            throw conflict("Для цієї філії не налаштовано активний ПРРО");
        }
        if (devices.size() != 1) {
            throw conflict("Для філії активовано кілька ПРРО; потрібно явно визначити касу");
        }

        Device device = devices.get(0);
        if (device.shiftKey() == null || device.shiftKey().isBlank()) {
            throw conflict("Спочатку відкрийте зміну ПРРО");
        }
        if (device.signerKeyId() == null || device.signerKeyId().isBlank()) {
            throw conflict("Для активної зміни ПРРО не визначено ключ підпису");
        }

        String requestHash = sha256(payment.fingerprint().getBytes(StandardCharsets.UTF_8));
        String idempotencyKey = "payment-request-" + paymentRequestKey;
        Operation operation = operations.prepare(new PrepareOperation(
                device.id(), device.shiftKey(), idempotencyKey, requestHash,
                "PAYMENT", paymentRequestKey, "CHK",
                authentication.getName(), device.signerKeyId()));

        db.update("""
                INSERT INTO prro_payment_operation_links
                    (payment_request_key, operation_id, branch_no, cash_account_id, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, paymentRequestKey, operation.id(), branchNo, payment.cashAccountId(),
                authentication.getName());

        return view(paymentRequestKey, operation, false);
    }

    @Transactional(readOnly = true)
    public PaymentOperationView status(String paymentRequestKey, long branchNo,
                                       org.springframework.security.core.Authentication authentication) {
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);
        Payment payment = readEligiblePayment(paymentRequestKey, branchNo);
        access.requireCustomerInBranch(payment.customerId(), payment.branchNo());

        List<Long> operationIds = db.query("""
                SELECT operation_id
                  FROM prro_payment_operation_links
                 WHERE payment_request_key = ? AND branch_no = ?
                """, (rs, rowNum) -> rs.getLong("operation_id"), paymentRequestKey, branchNo);
        if (operationIds.isEmpty()) {
            return new PaymentOperationView(paymentRequestKey, null, "NOT_PREPARED", false,
                    "Платіж ще не підготовлено до фіскалізації");
        }
        return view(paymentRequestKey, operations.get(operationIds.get(0)), true);
    }

    private Payment lockEligiblePayment(String requestKey, long branchNo) {
        List<PaymentRequest> rows = db.query("""
                SELECT customer_id, branch_no, payload, result
                  FROM customer_payment_requests
                 WHERE request_key = ? AND branch_no = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new PaymentRequest(rs.getLong("customer_id"),
                rs.getLong("branch_no"), rs.getString("payload"), rs.getString("result")),
                requestKey, branchNo);
        if (rows.isEmpty()) {
            throw conflict("Платіж не знайдено у вказаній філії");
        }
        return resolvePayment(requestKey, rows.get(0));
    }

    private Payment readEligiblePayment(String requestKey, long branchNo) {
        List<PaymentRequest> rows = db.query("""
                SELECT customer_id, branch_no, payload, result
                  FROM customer_payment_requests
                 WHERE request_key = ? AND branch_no = ?
                """, (rs, rowNum) -> new PaymentRequest(rs.getLong("customer_id"),
                rs.getLong("branch_no"), rs.getString("payload"), rs.getString("result")),
                requestKey, branchNo);
        if (rows.isEmpty()) {
            throw conflict("Платіж не знайдено у вказаній філії");
        }
        return resolvePayment(requestKey, rows.get(0));
    }

    /**
     * Returns a payment's fiscal test-check data after independently validating
     * the saved request, customer transactions, and source ledger entries.
     * Customer names, phone numbers, and addresses are deliberately not returned.
     */
    @Transactional(readOnly = true)
    public PaymentReceiptSource receiptSource(String paymentRequestKey, long branchNo,
                                               org.springframework.security.core.Authentication authentication) {
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);
        List<PaymentRequest> rows = db.query("""
                SELECT customer_id, branch_no, payload, result
                  FROM customer_payment_requests
                 WHERE request_key = ? AND branch_no = ?
                """, (rs, rowNum) -> new PaymentRequest(rs.getLong("customer_id"),
                rs.getLong("branch_no"), rs.getString("payload"), rs.getString("result")),
                paymentRequestKey, branchNo);
        if (rows.isEmpty()) throw conflict("Платіж не знайдено у вказаній філії");
        PaymentRequest request = rows.get(0);
        access.requireCustomerInBranch(request.customerId(), request.branchNo());
        return receiptSource(paymentRequestKey, request);
    }

    /** Lists recent eligible own-business cash/bank payments without customer PII. */
    @Transactional(readOnly = true)
    public List<RecentPayment> recentPayments(long branchNo,
                                               org.springframework.security.core.Authentication authentication) {
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);
        List<RecentRequest> requests = db.query("""
                SELECT request_key, customer_id, branch_no, payload, result, created_at
                  FROM customer_payment_requests
                 WHERE branch_no = ?
                 ORDER BY created_at DESC
                 LIMIT 40
                """, (rs, rowNum) -> new RecentRequest(rs.getString("request_key"),
                rs.getLong("customer_id"), rs.getLong("branch_no"), rs.getString("payload"),
                rs.getString("result"), rs.getTimestamp("created_at")), branchNo);

        List<RecentPayment> result = new ArrayList<>();
        for (RecentRequest item : requests) {
            try {
                access.requireCustomerInBranch(item.customerId(), item.branchNo());
                PaymentReceiptSource source = receiptSource(item.requestKey(),
                        new PaymentRequest(item.customerId(), item.branchNo(), item.payload(), item.result()));
                OperationLinkState state = db.query("""
                        SELECT o.status, o.fiscal_document_id
                          FROM prro_payment_operation_links l
                          JOIN prro_operations o ON o.id = l.operation_id
                         WHERE l.payment_request_key = ?
                        """, rs -> rs.next()
                        ? new OperationLinkState(rs.getString("status"), rs.getString("fiscal_document_id"))
                        : new OperationLinkState("NOT_PREPARED", null), item.requestKey());
                result.add(new RecentPayment(item.requestKey(), item.createdAt() == null
                        ? "" : item.createdAt().toInstant().toString(), source.documentLabel(),
                        source.amount(), source.paymentName(), state.status(), state.fiscalDocumentId()));
            } catch (ResponseStatusException | IllegalArgumentException ignored) {
                // Skip reversals, advance-only allocations, partner/non-cash
                // references, stale requests, and records that cannot be tied
                // unambiguously to a single incoming ledger transaction.
            }
        }
        return List.copyOf(result);
    }

    private PaymentReceiptSource receiptSource(String paymentRequestKey, PaymentRequest request) {
        Payment payment = resolvePayment(paymentRequestKey, request);
        if (payment.receivedAmount().signum() <= 0) {
            throw conflict("Немає нового надходження коштів для тестового чека.");
        }

        JsonNode payload;
        try {
            payload = json.readTree(request.payload());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не вдалося прочитати параметри платежу", ex);
        }

        String accountKind = db.query("""
                SELECT kind FROM cash_accounts WHERE id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, payment.cashAccountId());
        if (accountKind == null || (!"BRANCH_CASH".equals(accountKind)
                && !"CENTRAL_CASH".equals(accountKind) && !"BANK".equals(accountKind))) {
            throw conflict("Джерело надходження не є касою або банківським рахунком підприємства.");
        }

        Long invoiceId = payload.hasNonNull("invoiceId") ? payload.path("invoiceId").asLong() : null;
        String documentLabel;
        List<ReceiptItem> items;
        if (invoiceId != null) {
            List<String> invoiceNumbers = db.query("""
                    SELECT CAST(invoice_no AS CHAR)
                      FROM invoices
                     WHERE id = ? AND customer_id = ?
                    """, (rs, rowNum) -> rs.getString(1), invoiceId, request.customerId());
            if (invoiceNumbers.isEmpty()) {
                throw conflict("Рахунок цього платежу не знайдено для його клієнта.");
            }
            String invoiceNumber = invoiceNumbers.get(0);
            List<ReceiptLineDraft> lines = invoiceReceiptLines(invoiceId, invoiceNumber);
            if (lines.isEmpty()) {
                lines = List.of(new ReceiptLineDraft(
                        "Виготовлення багетної рами (Рахунок №" + invoiceNumber + ")",
                        BigDecimal.ONE));
            }
            documentLabel = "Рахунок №" + invoiceNumber + " · " + lines.size()
                    + (lines.size() == 1 ? " виріб" : " вироби");
            items = materializeReceiptItems(lines, payment.receivedAmount());
        } else {
            documentLabel = "Аванс клієнта";
            items = List.of(new ReceiptItem(documentLabel, payment.receivedAmount()));
        }

        BigDecimal amount;
        try {
            amount = payment.receivedAmount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw conflict("Сума нового надходження має містити не більше двох знаків після коми.");
        }

        boolean cash = "BRANCH_CASH".equals(accountKind) || "CENTRAL_CASH".equals(accountKind);
        return new PaymentReceiptSource(paymentRequestKey, request.branchNo(), payment.cashAccountId(),
                amount, invoiceId, documentLabel, items, cash ? "CASH" : "OTHER",
                cash ? "Готівка" : "Безготівкова оплата");
    }

    /**
     * Builds one receipt line per physical Baget product in every order attached
     * to this invoice. The JSON snapshot is the same immutable draft that was
     * used to calculate and save the order.
     */
    private List<ReceiptLineDraft> invoiceReceiptLines(Long invoiceId, String invoiceNumber) {
        List<InvoiceOrderProductRow> rows = db.query("""
                SELECT io.order_no, io.amount AS invoice_order_amount,
                       op.id AS product_id, op.total AS product_total,
                       op.configuration
                  FROM invoice_orders io
                  LEFT JOIN order_products op
                    ON op.order_no = io.order_no AND op.active = TRUE
                 WHERE io.invoice_id = ?
                 ORDER BY io.order_no, op.id
                """, (rs, rowNum) -> new InvoiceOrderProductRow(
                rs.getLong("order_no"), rs.getBigDecimal("invoice_order_amount"),
                rs.getObject("product_id") == null ? null : rs.getLong("product_id"),
                rs.getBigDecimal("product_total"), rs.getString("configuration")), invoiceId);

        if (rows.isEmpty()) return List.of();

        Map<Long, List<InvoiceOrderProductRow>> byOrder = new LinkedHashMap<>();
        for (InvoiceOrderProductRow row : rows) {
            byOrder.computeIfAbsent(row.orderNo(), ignored -> new ArrayList<>()).add(row);
        }

        List<ReceiptLineDraft> result = new ArrayList<>();
        for (Map.Entry<Long, List<InvoiceOrderProductRow>> entry : byOrder.entrySet()) {
            long orderNo = entry.getKey();
            List<InvoiceOrderProductRow> products = entry.getValue().stream()
                    .filter(row -> row.productId() != null)
                    .toList();
            BigDecimal invoiceOrderAmount = entry.getValue().get(0).invoiceOrderAmount();

            if (products.isEmpty()) {
                // Legacy orders may not have an order_products snapshot. Keep
                // the order identifiable rather than inventing dimensions.
                BigDecimal legacyWeight = invoiceOrderAmount == null || invoiceOrderAmount.signum() <= 0
                        ? BigDecimal.ONE : invoiceOrderAmount;
                result.add(new ReceiptLineDraft(
                        "Виготовлення багетної рами (Замовлення №" + orderNo + ")",
                        legacyWeight));
                continue;
            }

            BigDecimal productsTotal = products.stream()
                    .map(row -> row.productTotal() == null ? BigDecimal.ZERO : row.productTotal())
                    .filter(value -> value.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BigDecimal> productWeights = new ArrayList<>();
            for (InvoiceOrderProductRow product : products) {
                BigDecimal weight = product.productTotal();
                productWeights.add(weight == null || weight.signum() <= 0
                        ? (productsTotal.signum() > 0 ? BigDecimal.ZERO : BigDecimal.ONE)
                        : weight);
            }
            if (invoiceOrderAmount == null || invoiceOrderAmount.signum() <= 0) {
                invoiceOrderAmount = productsTotal.signum() > 0 ? productsTotal : BigDecimal.valueOf(products.size());
            }
            List<BigDecimal> orderShares = allocateProportionally(invoiceOrderAmount, productWeights);

            for (int index = 0; index < products.size(); index++) {
                InvoiceOrderProductRow product = products.get(index);
                ProductDimensions dimensions = readDimensions(product.configuration());
                StringBuilder name = new StringBuilder("Виготовлення багетної рами")
                        .append(" (Замовлення №").append(orderNo)
                        .append(", виріб №").append(index + 1);
                if (dimensions != null && dimensions.widthMm() != null && dimensions.heightMm() != null) {
                    name.append(", ").append(formatDimension(dimensions.widthMm()))
                            .append(" × ").append(formatDimension(dimensions.heightMm())).append(" мм");
                }
                name.append(')');
                result.add(new ReceiptLineDraft(name.toString(), orderShares.get(index)));
            }
        }
        return List.copyOf(result);
    }

    private ProductDimensions readDimensions(String configuration) {
        if (configuration == null || configuration.isBlank()) return null;
        try {
            JsonNode root = json.readTree(configuration);
            JsonNode draft = root.path("draft");
            JsonNode components = draft.path("components");
            if (components.isArray()) {
                for (JsonNode component : components) {
                    JsonNode source = component.path("product");
                    if (!source.isObject()) source = component.path("mounting");
                    if (source.isObject() && source.hasNonNull("widthMm") && source.hasNonNull("heightMm")) {
                        BigDecimal width = source.path("widthMm").decimalValue();
                        BigDecimal height = source.path("heightMm").decimalValue();
                        if (width.signum() > 0 && height.signum() > 0) {
                            return new ProductDimensions(width, height);
                        }
                    }
                }
            }
            // A defensive fallback for older configuration snapshots.
            JsonNode form = draft.path("form");
            if (form.hasNonNull("widthMm") && form.hasNonNull("heightMm")) {
                BigDecimal width = form.path("widthMm").decimalValue();
                BigDecimal height = form.path("heightMm").decimalValue();
                if (width.signum() > 0 && height.signum() > 0) {
                    return new ProductDimensions(width, height);
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // A malformed or legacy snapshot must not prevent a receipt from
            // identifying the order and product number.
        }
        return null;
    }

    private static String formatDimension(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static List<ReceiptItem> materializeReceiptItems(List<ReceiptLineDraft> lines,
                                                              BigDecimal totalAmount) {
        List<BigDecimal> amounts = allocateProportionally(totalAmount,
                lines.stream().map(ReceiptLineDraft::weight).toList());
        List<ReceiptItem> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            result.add(new ReceiptItem(lines.get(index).name(), amounts.get(index)));
        }
        return List.copyOf(result);
    }

    /**
     * Allocates integer kopecks proportionally, using largest remainders so
     * that every line is rounded to 2 decimals and the sum remains exact.
     */
    static List<BigDecimal> allocateProportionally(BigDecimal total, List<BigDecimal> weights) {
        if (total == null || total.signum() < 0 || weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("A non-negative total and at least one weight are required");
        }
        long totalKopecks = total.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        List<BigDecimal> normalized = weights.stream()
                .map(weight -> weight == null || weight.signum() < 0 ? BigDecimal.ZERO : weight)
                .toList();
        BigDecimal weightTotal = normalized.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightTotal.signum() == 0) {
            normalized = weights.stream().map(ignored -> BigDecimal.ONE).toList();
            weightTotal = BigDecimal.valueOf(normalized.size());
        }

        List<AllocationPart> parts = new ArrayList<>();
        long allocatedKopecks = 0;
        for (int index = 0; index < normalized.size(); index++) {
            BigDecimal exact = BigDecimal.valueOf(totalKopecks).multiply(normalized.get(index))
                    .divide(weightTotal, 24, RoundingMode.DOWN);
            long whole = exact.setScale(0, RoundingMode.DOWN).longValueExact();
            allocatedKopecks += whole;
            parts.add(new AllocationPart(index, whole, exact.subtract(BigDecimal.valueOf(whole))));
        }

        long remainder = totalKopecks - allocatedKopecks;
        List<AllocationPart> ranked = parts.stream()
                .sorted(Comparator.comparing(AllocationPart::fraction).reversed()
                        .thenComparingInt(AllocationPart::index))
                .toList();
        long[] result = new long[parts.size()];
        for (AllocationPart part : parts) result[part.index()] = part.kopecs();
        for (int index = 0; index < remainder; index++) {
            AllocationPart part = ranked.get(index);
            result[part.index()]++;
        }

        List<BigDecimal> amounts = new ArrayList<>();
        for (long value : result) amounts.add(BigDecimal.valueOf(value, 2));
        return List.copyOf(amounts);
    }

    private Payment resolvePayment(String requestKey, PaymentRequest request) {
        try {
            JsonNode payload = json.readTree(request.payload());
            BigDecimal receivedAmount = payload.path("amount").decimalValue();
            if (receivedAmount.signum() <= 0) {
                throw conflict("Цей запис не містить нового надходження коштів; зарахування авансу не фіскалізується повторно");
            }

            JsonNode result = json.readTree(request.result());
            if (!result.isArray()) {
                throw conflict("Не вдалося визначити платежі, створені цим запитом");
            }

            List<IncomingTransaction> transactions = new ArrayList<>();
            for (JsonNode item : result) {
                String type = item.path("type").asText("");
                if (!"PAYMENT".equals(type) && !"ADVANCE".equals(type)) {
                    continue;
                }
                long transactionId = item.path("id").asLong(0);
                if (transactionId <= 0) {
                    throw conflict("У відповіді платежу відсутній ідентифікатор транзакції");
                }
                List<IncomingTransaction> ledgerRows = db.query("""
                        SELECT ct.id, ct.amount, le.id AS ledger_id, le.cash_account_id,
                               le.amount AS ledger_amount
                          FROM customer_transactions ct
                          JOIN ledger_entries le ON le.customer_tx_id = ct.id
                         WHERE ct.id = ? AND ct.customer_id = ? AND ct.branch_id = ?
                           AND ct.active = TRUE AND ct.type IN ('PAYMENT', 'ADVANCE')
                           AND ct.amount > 0 AND le.direction = 'IN'
                           AND le.cash_account_id IS NOT NULL
                         ORDER BY le.id
                        """, (rs, rowNum) -> new IncomingTransaction(rs.getLong("id"),
                        rs.getBigDecimal("amount"), rs.getLong("ledger_id"),
                        rs.getLong("cash_account_id"), rs.getBigDecimal("ledger_amount")),
                        transactionId, request.customerId(), request.branchNo());
                if (ledgerRows.size() != 1) {
                    throw conflict("Не вдалося однозначно зіставити платіж із касовим записом");
                }
                transactions.add(ledgerRows.get(0));
            }

            if (transactions.isEmpty()) {
                throw conflict("У запиті немає нового надходження коштів для фіскалізації");
            }

            BigDecimal transactionTotal = transactions.stream().map(IncomingTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ledgerTotal = transactions.stream().map(IncomingTransaction::ledgerAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (transactionTotal.compareTo(receivedAmount) != 0
                    || ledgerTotal.compareTo(receivedAmount) != 0) {
                throw conflict("Сума касових записів не збігається із сумою нового надходження");
            }

            long cashAccountId = transactions.get(0).cashAccountId();
            if (transactions.stream().anyMatch(tx -> tx.cashAccountId() != cashAccountId)) {
                throw conflict("Частини одного платежу записані в різні каси; потрібна ручна перевірка");
            }

            String fingerprint = requestKey + "|" + request.customerId() + "|" + request.branchNo()
                    + "|" + receivedAmount.stripTrailingZeros().toPlainString() + "|"
                    + transactions.stream().map(tx -> tx.id() + ":" + tx.ledgerId() + ":"
                    + tx.amount().stripTrailingZeros().toPlainString() + ":" + tx.cashAccountId())
                    .sorted().reduce((a, b) -> a + "," + b).orElse("");
            return new Payment(requestKey, request.customerId(), request.branchNo(),
                    receivedAmount, cashAccountId, fingerprint);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не вдалося прочитати збережений платіж", ex);
        }
    }

    private static PaymentOperationView view(String requestKey, Operation operation, boolean existingLink) {
        String message = switch (operation.status()) {
            case PREPARED -> "Операцію прив'язано до платежу; чек ще не підписаний і не відправлений до ДПС";
            case SIGNED -> "Документ підписано, але результат відправлення ще не підтверджено";
            case SENDING -> "Надсилання документа виконується";
            case FISCALIZED -> "Чек фіскалізовано";
            case REJECTED -> "ДПС відхилила документ";
            case UNKNOWN_RESULT -> "Невідомий результат; перед повторною спробою потрібна звірка з ДПС";
            case CANCELLED -> "Локальну операцію скасовано до надсилання документа до ДПС";
        };
        return new PaymentOperationView(requestKey, operation.id(), operation.status().name(),
                existingLink, message);
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record PaymentOperationView(String paymentRequestKey, Long operationId, String status,
                                       boolean alreadyLinked, String message) {}
    public record ReceiptItem(String name, BigDecimal amount) {}
    public record PaymentReceiptSource(String paymentRequestKey, long branchNo, long cashAccountId,
                                       BigDecimal amount, Long invoiceId, String documentLabel,
                                       List<ReceiptItem> items, String paymentType, String paymentName) {
        public PaymentReceiptSource {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
    public record RecentPayment(String paymentRequestKey, String createdAt, String documentLabel,
                                BigDecimal amount, String paymentName, String operationStatus,
                                String fiscalDocumentId) {}
    private record ReceiptLineDraft(String name, BigDecimal weight) {}
    private record ProductDimensions(BigDecimal widthMm, BigDecimal heightMm) {}
    private record InvoiceOrderProductRow(long orderNo, BigDecimal invoiceOrderAmount,
                                          Long productId, BigDecimal productTotal, String configuration) {}
    private record AllocationPart(int index, long kopecs, BigDecimal fraction) {}
    private record RecentRequest(String requestKey, long customerId, long branchNo,
                                 String payload, String result, Timestamp createdAt) {}
    private record OperationLinkState(String status, String fiscalDocumentId) {}
    private record PaymentRequest(long customerId, long branchNo, String payload, String result) {}
    private record IncomingTransaction(long id, BigDecimal amount, long ledgerId,
                                       long cashAccountId, BigDecimal ledgerAmount) {}
    private record Payment(String requestKey, long customerId, long branchNo, BigDecimal receivedAmount,
                           long cashAccountId, String fingerprint) {}
    private record Device(long id, String shiftKey, String signerKeyId) {}
    private record Link(long operationId, long branchNo, long cashAccountId) {}
}
