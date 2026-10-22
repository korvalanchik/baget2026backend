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
 *
 * No call to DPS is made here. Network submission must be performed only after
 * a signed document and its hash have been durably recorded.
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
            if (!existing.requestHash().equalsIgnoreCase(command.requestHash())) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used for a different request");
            }
            return existing;
        }

        int localNumber = allocateLocalNumber(device, command.sourceType());
        jdbcTemplate.update("""
                INSERT INTO prro_operations
                    (device_id, idempotency_key, request_hash, source_type, source_reference,
                     document_type, local_number, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PREPARED', ?)
                """,
                command.deviceId(), command.idempotencyKey(), command.requestHash().toLowerCase(Locale.ROOT),
                command.sourceType(), command.sourceReference(), command.documentType(),
                localNumber, command.createdBy());

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
        return findById(operationId);
    }

    @Transactional(readOnly = true)
    public Operation get(long operationId) {
        return findById(operationId);
    }

    private Device lockDevice(long deviceId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, environment, enabled, next_local_number
                      FROM prro_devices
                     WHERE id = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new Device(
                    rs.getLong("id"),
                    rs.getString("environment"),
                    rs.getBoolean("enabled"),
                    rs.getInt("next_local_number")), deviceId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("PRRO device not found: " + deviceId, ex);
        }
    }

    private int allocateLocalNumber(Device device, String sourceType) {
        if (!device.enabled()) {
            throw new IllegalStateException("PRRO device is disabled");
        }
        if ("SHIFT_OPEN".equals(sourceType)) {
            // A shift-opening service document must always have local number 0.
            // It does not consume the ordinary sales sequence.
            return 0;
        }

        int localNumber = device.nextLocalNumber();
        if (localNumber < 1 || localNumber > 2_147_483_646) {
            throw new IllegalStateException("PRRO local receipt number range is exhausted");
        }
        jdbcTemplate.update("UPDATE prro_devices SET next_local_number = ? WHERE id = ?",
                localNumber + 1, device.id());
        return localNumber;
    }

    private Operation findByIdempotencyKey(long deviceId, String key) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, device_id, idempotency_key, request_hash, source_type,
                           source_reference, document_type, local_number, status,
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
                    SELECT id, device_id, idempotency_key, request_hash, source_type,
                           source_reference, document_type, local_number, status,
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
        requireText(command.idempotencyKey(), "idempotencyKey", 100);
        requireText(command.requestHash(), "requestHash", 64);
        requireText(command.sourceType(), "sourceType", 24);
        requireText(command.documentType(), "documentType", 24);
        requireText(command.createdBy(), "createdBy", 150);
        if (!SHA_256.matcher(command.requestHash()).matches()) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 hex string");
        }
        if (!("PAYMENT".equals(command.sourceType()) || "SHIFT_OPEN".equals(command.sourceType())
                || "SHIFT_CLOSE".equals(command.sourceType()) || "SERVICE".equals(command.sourceType()))) {
            throw new IllegalArgumentException("Unsupported PRRO sourceType");
        }
        if (!("CHK".equals(command.documentType()) || "ZREPORT".equals(command.documentType())
                || "SERVICECHK".equals(command.documentType()))) {
            throw new IllegalArgumentException("Unsupported PRRO documentType");
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
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record Device(long id, String environment, boolean enabled, int nextLocalNumber) {}

    public record PrepareOperation(long deviceId, String idempotencyKey, String requestHash,
                                   String sourceType, String sourceReference, String documentType,
                                   String createdBy) {}

    public record Operation(long id, long deviceId, String idempotencyKey, String requestHash,
                            String sourceType, String sourceReference, String documentType,
                            int localNumber, Status status, String signedDocumentHash,
                            String fiscalDocumentId, Integer responseCode, String responseMessage,
                            String createdBy) {}

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
        String status = rs.getString("status");
        return new Operation(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("source_type"),
                rs.getString("source_reference"),
                rs.getString("document_type"),
                rs.getInt("local_number"),
                Status.valueOf(status),
                rs.getString("signed_document_hash"),
                rs.getString("fiscal_document_id"),
                responseCodeValue,
                rs.getString("response_message"),
                rs.getString("created_by"));
    };
}
