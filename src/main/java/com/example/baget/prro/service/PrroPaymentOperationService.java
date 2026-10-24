package com.example.baget.prro.service;

import com.example.baget.finance.CustomerPaymentAccess;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Connects one saved customer payment request to at most one PRRO operation.
 * It only prepares local state; it does not construct a receipt or contact DPS.
 */
@Service
public class PrroPaymentOperationService {
    private final JdbcTemplate db;
    private final CustomerPaymentAccess access;
    private final PrroOperationService operations;
    private final ObjectMapper json;

    public PrroPaymentOperationService(JdbcTemplate db, CustomerPaymentAccess access,
                                       PrroOperationService operations, ObjectMapper json) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
        this.operations = Objects.requireNonNull(operations);
        this.json = Objects.requireNonNull(json);
    }

    @Transactional
    public PaymentOperationView prepare(String paymentRequestKey, long branchNo,
                                        org.springframework.security.core.Authentication authentication) {
        User user = access.user(authentication);
        Payment payment = lockEligiblePayment(paymentRequestKey, branchNo);
        access.customer(user, payment.customerId(), payment.branchNo());

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
        User user = access.user(authentication);
        Payment payment = readEligiblePayment(paymentRequestKey, branchNo);
        access.customer(user, payment.customerId(), payment.branchNo());

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
    private record PaymentRequest(long customerId, long branchNo, String payload, String result) {}
    private record IncomingTransaction(long id, BigDecimal amount, long ledgerId,
                                       long cashAccountId, BigDecimal ledgerAmount) {}
    private record Payment(String requestKey, long customerId, long branchNo, BigDecimal receivedAmount,
                           long cashAccountId, String fingerprint) {}
    private record Device(long id, String shiftKey, String signerKeyId) {}
    private record Link(long operationId, long branchNo, long cashAccountId) {}
}
