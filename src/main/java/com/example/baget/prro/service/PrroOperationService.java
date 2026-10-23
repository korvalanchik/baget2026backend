package com.example.baget.prro.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persistence and idempotency boundary for PRRO operations.
 * No call to DPS is made here.
 */
@Service
public class PrroOperationService {

    private static final Pattern SHA_256 = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final JdbcTemplate jdbcTemplate;

    public PrroOperationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Transactional
    public Operation prepare(PrepareOperation command) {
        validate(command);

        Device device = lockDevice(command.deviceId());
        Operation existing = findByIdempotencyKey(command.deviceId(), command.idempotencyKey());
        if (existing != null) {
            if (!existing.requestHash().equalsIgnoreCase(command.requestHash())
                    || !existing.shiftKey().equals(command.shiftKey())
                    || !Objects.equals(existing.signerKeyId(), command.signerKeyId())) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used for a different request");
            }
            return existing;
        }

        int localNumber = allocateLocalNumber(device, command);
        jdbcTemplate.update("""
                INSERT INTO prro_operations
                    (device_id, shift_key, idempotency_key, request_hash, source_type, source_reference,
                     document_type, local_number, signer_key_id, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED', ?)
                """,
                command.deviceId(), command.shiftKey(), command.idempotencyKey(),
                command.requestHash().toLowerCase(Locale.ROOT), command.sourceType(),
                command.sourceReference(), command.documentType(), localNumber,
                command.signerKeyId(), command.createdBy());

        return findByIdempotencyKey(command.deviceId(), command.idempotencyKey());
    }

    @Transactional
    public Operation transition(long operationId, Status expected, Status next,
                                String signedDocumentHash, String fiscalDocumentId,
                                Integer responseCode, String responseMessage) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        validateTransition(expected, next);
        if (signedDocumentHash != null && !SHA_256.matcher(signedDocumentHash).matches()) {
            throw new IllegalArgumentException("signedDocumentHash must be a SHA-256 hex string");
        }

        Operation before = findById(operationId);
        int changed = jdbcTemplate.update("""
                UPDATE prro_operations
                   SET status = ?,
                       signed_document_hash = COALESCE(?, signed_document_hash),
                       fiscal_document_id = COALESCE(?, fiscal_document_id),
                       response_code = ?,
                       response_message = ?
                 WHERE id = ? AND status = ?
                """,
                next.name(), signedDocumentHash, fiscalDocumentId, responseCode,
                truncate(responseMessage, 500), operationId, expected.name());

        if (changed != 1) {
            throw new OperationStateConflictException(
                    "PRRO operation was not in expected state " + expected);
        }

        if (before.sourceType().equals("SHIFT_OPEN") && next == Status.REJECTED) {
            jdbcTemplate.update("""
                    UPDATE prro_devices
                       SET active_shift_key = NULL, active_signer_key_id = NULL
                     WHERE id = ? AND active_shift_key = ?
                    """, before.deviceId(), before.shiftKey());
        }
        if (before.sourceType().equals("SHIFT_CLOSE") && next == Status.FISCALIZED) {
            jdbcTemplate.update("""
                    UPDATE prro_devices
                       SET active_shift_key = NULL, active_signer_key_id = NULL
                     WHERE id = ? AND active_shift_key = ?
                    """, before.deviceId(), before.shiftKey());
        }
        return findById(operationId);
    }

    @Transactional(readOnly = true)
    public Operation get(long operationId) {
        return findById(operationId);
    }

    private Device lockDevice(long deviceId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, environment, enabled, active_shift_key, active_signer_key_id,
                           next_local_number
                      FROM prro_devices
                     WHERE id = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new Device(
                    rs.getLong("id"),
                    rs.getString("environment"),
                    rs.getBoolean("enabled"),
                    rs.getString("active_shift_key"),
                    rs.getString("active_signer_key_id"),
                    rs.getInt("next_local_number")), deviceId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("PRRO device not found: " + deviceId, ex);
        }
    }

    private int allocateLocalNumber(Device device, PrepareOperation command) {
        if (!device.enabled()) {
            throw new IllegalStateException("PRRO device is disabled");
        }

        if ("SHIFT_OPEN".equals(command.sourceType())) {
            if (device.activeShiftKey() != null) {
                throw new IllegalStateException("PRRO device already has an active shift");
            }
            jdbcTemplate.update("""
                    UPDATE prro_devices
                       SET active_shift_key = ?, active_signer_key_id = ?
                     WHERE id = ? AND active_shift_key IS NULL
                    """, command.shiftKey(), command.signerKeyId(), device.id());
            return 0;
        }

        if (device.activeShiftKey() == null || !device.activeShiftKey().equals(command.shiftKey())) {
            throw new IllegalStateException("The requested PRRO shift is not active");
        }
        validateActiveSignerKey(device.activeSignerKeyId(), command.signerKeyId());

        int localNumber = device.nextLocalNumber();
        if (localNumber < 1 || localNumber > 2_147_483_646) {
            throw new IllegalStateException("PRRO local receipt number range is exhausted");
        }
        jdbcTemplate.update("UPDATE prro_devices SET next_local_number = ? WHERE id = ?",
                localNumber + 1, device.id());
        return localNumber;
    }

    static void validateActiveSignerKey(String activeSignerKeyId, String requestedSignerKeyId) {
        if (activeSignerKeyId == null || activeSignerKeyId.isBlank()) {
            throw new IllegalStateException(
                    "The active PRRO shift has no recorded signer; reconcile the shift before preparing documents");
        }
        if (!activeSignerKeyId.equals(requestedSignerKeyId)) {
            throw new IllegalStateException(
                    "All documents in an active PRRO shift must use the same signer key");
        }
    }

    private Operation findByIdempotencyKey(long deviceId, String key) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, device_id, shift_key, idempotency_key, request_hash, source_type,
                           source_reference, document_type, local_number, signer_key_id, status,
                           signed_document_hash, fiscal_document_id, response_code,
                           response_message, created_by
                      FROM prro_operations
                     WHERE device_id = ? AND idempotency_key = ?
                    """, OPERATION_ROW_MAPPER, deviceId, key);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Operation findById(long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, device_id, shift_key, idempotency_key, request_hash, source_type,
                           source_reference, document_type, local_number, signer_key_id, status,
                           signed_document_hash, fiscal_document_id, response_code,
                           response_message, created_by
                      FROM prro_operations
                     WHERE id = ?
                    """, OPERATION_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("PRRO operation not found: " + id, ex);
        }
    }

    private void validate(PrepareOperation command) {
        Objects.requireNonNull(command, "command");
        requireText(command.shiftKey(), "shiftKey", 64);
        requireText(command.idempotencyKey(), "idempotencyKey", 100);
        requireText(command.requestHash(), "requestHash", 64);
        requireText(command.sourceType(), "sourceType", 24);
        requireText(command.documentType(), "documentType", 24);
        requireText(command.createdBy(), "createdBy", 150);
        requireText(command.signerKeyId(), "signerKeyId", 128);
        if (!SHA_256.matcher(command.requestHash()).matches()) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 hex string");
        }

        boolean supportedSource = "PAYMENT".equals(command.sourceType())
                || "SHIFT_OPEN".equals(command.sourceType())
                || "SHIFT_CLOSE".equals(command.sourceType())
                || "SERVICE".equals(command.sourceType());
        boolean supportedDocument = "CHK".equals(command.documentType())
                || "ZREPORT".equals(command.documentType())
                || "SERVICECHK".equals(command.documentType());
        if (!supportedSource || !supportedDocument) {
            throw new IllegalArgumentException("Unsupported PRRO source or document type");
        }
        if ("PAYMENT".equals(command.sourceType()) && !"CHK".equals(command.documentType())) {
            throw new IllegalArgumentException("PAYMENT operations must use CHK documents");
        }
        if ("SHIFT_OPEN".equals(command.sourceType()) && !"SERVICECHK".equals(command.documentType())) {
            throw new IllegalArgumentException("SHIFT_OPEN operations must use SERVICECHK documents");
        }
        if ("SHIFT_CLOSE".equals(command.sourceType()) && !"ZREPORT".equals(command.documentType())) {
            throw new IllegalArgumentException("SHIFT_CLOSE operations must use ZREPORT documents");
        }
        if (command.sourceReference() != null && command.sourceReference().length() > 128) {
            throw new IllegalArgumentException("sourceReference exceeds 128 characters");
        }
    }

    private void validateTransition(Status expected, Status next) {
        boolean allowed = switch (expected) {
            case PREPARED -> next == Status.SIGNED || next == Status.REJECTED;
            case SIGNED -> next == Status.SENDING || next == Status.REJECTED;
            case SENDING -> next == Status.FISCALIZED || next == Status.REJECTED
                    || next == Status.UNKNOWN_RESULT;
            case UNKNOWN_RESULT -> next == Status.FISCALIZED || next == Status.REJECTED;
            case FISCALIZED, REJECTED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Invalid PRRO state transition: " + expected + " -> " + next);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must be at most "
                    + maxLength + " characters");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Device(long id, String environment, boolean enabled, String activeShiftKey,
                          String activeSignerKeyId, int nextLocalNumber) {}

    public record PrepareOperation(long deviceId, String shiftKey, String idempotencyKey,
                                   String requestHash, String sourceType, String sourceReference,
                                   String documentType, String createdBy, String signerKeyId) {
        public PrepareOperation(long deviceId, String shiftKey, String idempotencyKey,
                                String requestHash, String sourceType, String sourceReference,
                                String documentType, String createdBy) {
            this(deviceId, shiftKey, idempotencyKey, requestHash, sourceType, sourceReference,
                    documentType, createdBy, null);
        }

        public PrepareOperation {
            signerKeyId = signerKeyId == null ? null : signerKeyId.trim();
        }
    }

    public record Operation(long id, long deviceId, String shiftKey, String idempotencyKey,
                            String requestHash, String sourceType, String sourceReference,
                            String documentType, int localNumber, String signerKeyId, Status status,
                            String signedDocumentHash, String fiscalDocumentId,
                            Integer responseCode, String responseMessage, String createdBy) {
        public Operation(long id, long deviceId, String shiftKey, String idempotencyKey,
                         String requestHash, String sourceType, String sourceReference,
                         String documentType, int localNumber, Status status,
                         String signedDocumentHash, String fiscalDocumentId,
                         Integer responseCode, String responseMessage, String createdBy) {
            this(id, deviceId, shiftKey, idempotencyKey, requestHash, sourceType, sourceReference,
                    documentType, localNumber, null, status, signedDocumentHash, fiscalDocumentId,
                    responseCode, responseMessage, createdBy);
        }
    }

    public enum Status {
        PREPARED, SIGNED, SENDING, FISCALIZED, REJECTED, UNKNOWN_RESULT
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }

    public static class OperationStateConflictException extends RuntimeException {
        public OperationStateConflictException(String message) { super(message); }
    }

    private static final RowMapper<Operation> OPERATION_ROW_MAPPER = (rs, rowNum) -> {
        int responseCode = rs.getInt("response_code");
        Integer responseCodeValue = rs.wasNull() ? null : responseCode;
        return new Operation(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("shift_key"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("source_type"),
                rs.getString("source_reference"),
                rs.getString("document_type"),
                rs.getInt("local_number"),
                rs.getString("signer_key_id"),
                Status.valueOf(rs.getString("status")),
                rs.getString("signed_document_hash"),
                rs.getString("fiscal_document_id"),
                responseCodeValue,
                rs.getString("response_message"),
                rs.getString("created_by"));
    };
}
