package com.example.baget.finance;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.customer.CustomerTransactionType;
import com.example.baget.my_company.CompanyDetailsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerPaymentReceiptService {
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper json;
    private final CustomerPaymentAccess access;
    private final CustomerPaymentReceiptPdf pdf;

    public CustomerPaymentReceiptService(
            JdbcTemplate db,
            NamedParameterJdbcTemplate named,
            ObjectMapper json,
            CustomerPaymentAccess access,
            CustomerPaymentReceiptPdf pdf) {
        this.db = db;
        this.named = named;
        this.json = json;
        this.access = access;
        this.pdf = pdf;
    }

    public record PdfReceipt(String receiptNumber, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PdfReceipt render(String requestKey, Authentication authentication) throws Exception {
        var user = access.user(authentication);
        try {
            if (!UUID.fromString(requestKey).toString().equalsIgnoreCase(requestKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw fail(HttpStatus.BAD_REQUEST, "Некоректний ключ платежу");
        }

        Map<String, Object> requestRows = singleRequest(requestKey);
        if (requestRows == null) throw fail(HttpStatus.NOT_FOUND, "Квитанцію за цим платежем не знайдено");

        long customerId = number(requestRows, "customerId");
        long branchNo = number(requestRows, "branchNo");
        access.customer(user, customerId, branchNo);

        JsonNode payload = parse(requestRows.get("payload"), "Параметри платежу не вдалося прочитати");
        JsonNode result = parse(requestRows.get("result"), "Результат платежу не вдалося прочитати");
        if (!result.isArray() || result.isEmpty()) {
            throw fail(HttpStatus.CONFLICT, "Платіж не містить записів для квитанції");
        }

        BigDecimal cashReceived = decimal(payload, "amount");
        BigDecimal advanceAllocated = decimal(payload, "allocationAmount");
        if (cashReceived.signum() < 0 || advanceAllocated.signum() < 0
                || cashReceived.add(advanceAllocated).signum() <= 0) {
            throw fail(HttpStatus.CONFLICT, "У платіжному записі немає суми для квитанції");
        }

        Long invoiceId = nullableLong(payload.get("invoiceId"));
        Long invoiceNo = null;
        if (invoiceId != null) {
            access.invoice(user, invoiceId);
            List<Long> invoiceCustomers = db.queryForList(
                    "SELECT customer_id FROM invoices WHERE id=?", Long.class, invoiceId);
            if (invoiceCustomers.isEmpty() || invoiceCustomers.get(0) != customerId) {
                throw fail(HttpStatus.CONFLICT, "Рахунок не відповідає клієнту платежу");
            }
            invoiceNo = db.queryForObject(
                    "SELECT invoice_no FROM invoices WHERE id=?", Long.class, invoiceId);
        }

        List<Long> transactionIds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode entry : result) {
            JsonNode id = entry.get("id");
            if (id == null || !id.canConvertToLong() || id.asLong() <= 0 || !seen.add(id.asLong())) {
                throw fail(HttpStatus.CONFLICT, "Результат платежу містить некоректні записи");
            }
            transactionIds.add(id.asLong());
        }
        if (transactionIds.isEmpty()) throw fail(HttpStatus.CONFLICT, "Платіжні записи не знайдені");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", transactionIds)
                .addValue("customerId", customerId)
                .addValue("branchNo", branchNo);
        List<Map<String, Object>> transactions = named.queryForList("""
                SELECT ct.id AS transactionId,ct.type,ct.amount AS transactionAmount,
                       ct.created_at AS createdAt,ct.note,
                       i.invoice_no AS transactionInvoiceNo
                FROM customer_transactions ct
                LEFT JOIN invoices i ON i.id=ct.invoice_id
                WHERE ct.id IN (:ids) AND ct.customer_id=:customerId
                  AND ct.branch_id=:branchNo AND ct.active=1
                ORDER BY ct.id
                """, params);
        if (transactions.size() != transactionIds.size()) {
            throw fail(HttpStatus.CONFLICT, "Не всі записи платежу доступні для квитанції");
        }

        List<Map<String, Object>> cashRows = named.queryForList("""
                SELECT le.customer_tx_id AS transactionId,le.amount,ca.name AS accountName,
                       ca.kind AS accountKind
                FROM ledger_entries le
                JOIN customer_transactions ct ON ct.id=le.customer_tx_id
                JOIN cash_accounts ca ON ca.id=le.cash_account_id
                WHERE le.customer_tx_id IN (:ids) AND ct.customer_id=:customerId
                  AND ct.branch_id=:branchNo AND ct.active=1
                  AND le.direction='IN'
                  AND le.category IN ('PAYMENT_RECEIVED','CUSTOMER_ADVANCE')
                  AND le.cash_account_id IS NOT NULL
                ORDER BY le.id
                """, params);
        BigDecimal ledgerCash = cashRows.stream()
                .map(row -> decimal(row.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ledgerCash.compareTo(cashReceived) != 0) {
            throw fail(HttpStatus.CONFLICT,
                    "Сума квитанції не збігається з фактичними надходженнями в касу або банк");
        }

        Map<String, BigDecimal> groupedCash = new LinkedHashMap<>();
        Map<String, String> accountKinds = new LinkedHashMap<>();
        for (Map<String, Object> row : cashRows) {
            String accountName = Objects.toString(row.get("accountName"), "Рахунок");
            String kind = Objects.toString(row.get("accountKind"), "");
            String key = accountName + "\u0000" + kind;
            groupedCash.merge(key, decimal(row.get("amount")), BigDecimal::add);
            accountKinds.put(key, kind);
        }
        List<CustomerPaymentReceiptPdf.CashLine> cashLines = groupedCash.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\u0000", -1);
                    return new CustomerPaymentReceiptPdf.CashLine(
                            parts[0], accountKinds.get(entry.getKey()), entry.getValue());
                })
                .toList();

        List<CustomerPaymentReceiptPdf.TransactionLine> lines = new ArrayList<>();
        for (Map<String, Object> row : transactions) {
            CustomerTransactionType type = CustomerTransactionType.valueOf(row.get("type").toString());
            BigDecimal amount = decimal(row.get("transactionAmount")).abs();
            String description = switch (type) {
                case PAYMENT -> "Оплата за рахунком № " +
                        Objects.toString(row.get("transactionInvoiceNo"), Objects.toString(invoiceNo, "—"));
                case ADVANCE -> invoiceNo == null ? "Авансовий платіж" : "Аванс / переплата під час оплати рахунку № " + invoiceNo;
                case ADVANCE_ALLOCATION -> "Зараховано з раніше внесеного авансу";
                default -> "Операція розрахунку";
            };
            lines.add(new CustomerPaymentReceiptPdf.TransactionLine(description, amount));
        }

        Map<String, Object> context = db.queryForMap("""
                SELECT c.Company AS customerName,
                       COALESCE(NULLIF(c.Mobile,''),NULLIF(c.Phone,''),'') AS customerPhone,
                       b.BranchName AS branchName
                FROM customer c CROSS JOIN branches b
                WHERE c.CustNo=? AND b.BranchNo=?
                """, customerId, branchNo);

        Object createdAt = transactions.get(0).get("createdAt");
        String createdAtText = dateTime(createdAt);
        long numberForReceipt = transactionIds.stream().mapToLong(Long::longValue).min().orElseThrow();
        var data = new CustomerPaymentReceiptPdf.ReceiptData(
                "CR-" + numberForReceipt,
                createdAtText,
                Objects.toString(context.get("customerName"), ""),
                Objects.toString(context.get("customerPhone"), ""),
                Objects.toString(context.get("branchName"), ""),
                Objects.toString(requestRows.get("actor"), ""),
                invoiceNo,
                cashReceived,
                advanceAllocated,
                nullableText(payload.get("note")),
                List.copyOf(lines),
                cashLines);
        return new PdfReceipt(data.receiptNumber(), pdf.render(data));
    }

    private Map<String, Object> singleRequest(String requestKey) {
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT customer_id AS customerId,branch_no AS branchNo,actor,payload,result
                FROM customer_payment_requests
                WHERE request_key=?
                """, requestKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private JsonNode parse(Object raw, String message) {
        if (raw == null) throw fail(HttpStatus.CONFLICT, message);
        try {
            return json.readTree(raw.toString());
        } catch (JsonProcessingException e) {
            throw fail(HttpStatus.CONFLICT, message);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            throw fail(HttpStatus.CONFLICT, "У платіжному записі відсутня сума: " + field);
        }
        return value.decimalValue();
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }

    private Long nullableLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.canConvertToLong() ? node.asLong() : null;
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number n)) throw fail(HttpStatus.CONFLICT, "Некоректні дані квитанції");
        return n.longValue();
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String dateTime(Object value) {
        if (value instanceof OffsetDateTime offset) return DATE_TIME.format(offset.atZoneSameInstant(KYIV));
        if (value instanceof Timestamp timestamp) return DATE_TIME.format(timestamp.toInstant().atZone(KYIV));
        if (value instanceof LocalDateTime local) return DATE_TIME.format(local);
        return Objects.toString(value, "—").replace('T', ' ');
    }

    private PreviewException fail(HttpStatus status, String message) {
        return new PreviewException(status, "PAYMENT_RECEIPT_ERROR", message);
    }
}
