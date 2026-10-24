package com.example.baget.prro.service;

import com.example.baget.finance.CustomerPaymentAccess;
import com.example.baget.prro.service.PrroOperationService.Operation;
import com.example.baget.prro.service.PrroOperationService.PrepareOperation;
import com.example.baget.prro.service.PrroOperationService.Status;
import com.example.baget.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Connects one recorded incoming customer payment to at most one PRRO operation.
 * It only prepares local state; it does not construct a receipt or contact DPS.
 */
@Service
public class PrroPaymentOperationService {
    private final JdbcTemplate db;
    private final CustomerPaymentAccess access;
    private final PrroOperationService operations;

    public PrroPaymentOperationService(JdbcTemplate db, CustomerPaymentAccess access,
                                       PrroOperationService operations) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
        this.operations = Objects.requireNonNull(operations);
    }

    @Transactional
    public PaymentOperationView prepare(long transactionId, long branchNo,
                                        org.springframework.security.core.Authentication authentication) {
        User user = access.user(authentication);
        Payment payment = lockEligiblePayment(transactionId, branchNo);
        access.customer(user, payment.customerId(), payment.branchNo());

        List<Link> links = db.query("""
                SELECT operation_id, branch_no, cash_account_id
                  FROM prro_payment_operation_links
                 WHERE customer_transaction_id = ?
                """, (rs, rowNum) -> new Link(rs.getLong("operation_id"),
                rs.getLong("branch_no"), rs.getLong("cash_account_id")), transactionId);
        if (!links.isEmpty()) {
            Link link = links.get(0);
            if (link.branchNo() != branchNo || link.cashAccountId() != payment.cashAccountId()) {
                throw conflict("Платіж уже пов'язаний з іншою філією або касою");
            }
            return view(operations.get(link.operationId()), true);
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
        String idempotencyKey = "payment-tx-" + transactionId;
        Operation operation = operations.prepare(new PrepareOperation(
                device.id(), device.shiftKey(), idempotencyKey, requestHash,
                "PAYMENT", Long.toString(transactionId), "CHK",
                authentication.getName(), device.signerKeyId()));

        // The payment row is locked above, so competing requests for this payment
        // serialize before this unique link is written.
        db.update("""
                INSERT INTO prro_payment_operation_links
                    (customer_transaction_id, operation_id, branch_no, cash_account_id, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, transactionId, operation.id(), branchNo, payment.cashAccountId(),
                authentication.getName());

        return view(operation, false);
    }

    @Transactional(readOnly = true)
    public PaymentOperationView status(long transactionId, long branchNo,
                                       org.springframework.security.core.Authentication authentication) {
        User user = access.user(authentication);
        Payment payment = readEligiblePayment(transactionId, branchNo);
        access.customer(user, payment.customerId(), payment.branchNo());

        List<Long> operationIds = db.query("""
                SELECT operation_id
                  FROM prro_payment_operation_links
                 WHERE customer_transaction_id = ? AND branch_no = ?
                """, (rs, rowNum) -> rs.getLong("operation_id"), transactionId, branchNo);
        if (operationIds.isEmpty()) {
            return new PaymentOperationView(transactionId, null, "NOT_PREPARED", false,
                    "Платіж ще не підготовлено до фіскалізації");
        }
        return view(operations.get(operationIds.get(0)), true);
    }

    private Payment lockEligiblePayment(long transactionId, long branchNo) {
        List<Payment> rows = db.query("""
                SELECT ct.id, ct.customer_id, ct.branch_id, ct.type, ct.amount, ct.created_at,
                       le.id AS ledger_id, le.cash_account_id
                  FROM customer_transactions ct
                  JOIN ledger_entries le ON le.customer_tx_id = ct.id
                 WHERE ct.id = ? AND ct.branch_id = ? AND ct.active = TRUE
                   AND ct.type IN ('PAYMENT', 'ADVANCE')
                   AND ct.amount > 0 AND le.direction = 'IN'
                   AND le.cash_account_id IS NOT NULL
                 ORDER BY le.id
                 LIMIT 1
                 FOR UPDATE
                """, PAYMENT_ROW, transactionId, branchNo);
        if (rows.isEmpty()) {
            throw conflict("Не знайдено нове надходження коштів у вказаній філії або платіж не прив'язаний до каси");
        }
        return rows.get(0);
    }

    private Payment readEligiblePayment(long transactionId, long branchNo) {
        List<Payment> rows = db.query("""
                SELECT ct.id, ct.customer_id, ct.branch_id, ct.type, ct.amount, ct.created_at,
                       le.id AS ledger_id, le.cash_account_id
                  FROM customer_transactions ct
                  JOIN ledger_entries le ON le.customer_tx_id = ct.id
                 WHERE ct.id = ? AND ct.branch_id = ? AND ct.active = TRUE
                   AND ct.type IN ('PAYMENT', 'ADVANCE')
                   AND ct.amount > 0 AND le.direction = 'IN'
                   AND le.cash_account_id IS NOT NULL
                 ORDER BY le.id
                 LIMIT 1
                """, PAYMENT_ROW, transactionId, branchNo);
        if (rows.isEmpty()) {
            throw conflict("Не знайдено нове надходження коштів у вказаній філії або платіж не прив'язаний до каси");
        }
        return rows.get(0);
    }

    private static PaymentOperationView view(Operation operation, boolean existingLink) {
        String message = switch (operation.status()) {
            case PREPARED -> "Операцію прив'язано до платежу; чек ще не підписаний і не відправлений до ДПС";
            case SIGNED -> "Документ підписано, але результат відправлення ще не підтверджено";
            case SENDING -> "Надсилання документа виконується";
            case FISCALIZED -> "Чек фіскалізовано";
            case REJECTED -> "ДПС відхилила документ";
            case UNKNOWN_RESULT -> "Невідомий результат; перед повторною спробою потрібна звірка з ДПС";
        };
        return new PaymentOperationView(parseTransactionId(operation.sourceReference()),
                operation.id(), operation.status().name(), existingLink, message);
    }

    private static long parseTransactionId(String sourceReference) {
        try {
            return Long.parseLong(sourceReference);
        } catch (RuntimeException ex) {
            return -1;
        }
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

    public record PaymentOperationView(long transactionId, Long operationId, String status,
                                       boolean alreadyLinked, String message) {}
    private record Link(long operationId, long branchNo, long cashAccountId) {}
    private record Device(long id, String shiftKey, String signerKeyId) {}
    private record Payment(long id, long customerId, long branchNo, String type, BigDecimal amount,
                           Timestamp createdAt, long ledgerId, long cashAccountId) {
        String fingerprint() {
            return id + "|" + customerId + "|" + branchNo + "|" + type + "|"
                    + amount.stripTrailingZeros().toPlainString() + "|" + createdAt + "|"
                    + ledgerId + "|" + cashAccountId;
        }
    }

    private static final org.springframework.jdbc.core.RowMapper<Payment> PAYMENT_ROW =
            (rs, rowNum) -> new Payment(rs.getLong("id"), rs.getLong("customer_id"),
                    rs.getLong("branch_id"), rs.getString("type"), rs.getBigDecimal("amount"),
                    rs.getTimestamp("created_at"), rs.getLong("ledger_id"),
                    rs.getLong("cash_account_id"));
}
