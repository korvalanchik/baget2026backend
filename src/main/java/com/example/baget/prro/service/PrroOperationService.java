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
        Operation existing = findByIdempotencyKeyInternal(command.deviceId(), command.idempotencyKey());
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

        return findByIdempotencyKeyInternal(command.deviceId(), command.idempotencyKey());
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
        if (next == Status.SENDING) {
            ensureSubmissionOrder(before);
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

        if (before.sourceType().equals("SHIFT_OPEN")
                && (next == Status.REJECTED || next == Status.CANCELLED)) {
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

    /**
     * Prevents overlapping or out-of-order submissions for a single PRRO device.
     *
     * <p>The device row lock serializes competing send attempts across application
     * instances. The locking read of active operations observes current database
     * state even if an earlier read in this transaction established a repeatable-read
     * snapshot.</p>
     */
    private void ensureSubmissionOrder(Operation candidate) {
        lockDevice(candidate.deviceId());

        var pending = jdbcTemplate.query("""
                SELECT id, local_number, status
                  FROM prro_operations
                 WHERE device_id = ?
                   AND status IN ('PREPARED', 'SIGNED', 'SENDING', 'UNKNOWN_RESULT')
                 ORDER BY local_number, id
                 FOR UPDATE
                """, (rs, rowNum) -> new PendingSubmission(
                rs.getLong("id"),
                rs.getInt("local_number"),
                Status.valueOf(rs.getString("status"))), candidate.deviceId());

        validateSubmissionOrder(candidate, pending);
    }

    static void validateSubmissionOrder(Operation candidate, java.util.List<PendingSubmission> pending) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(pending, "pending");

        for (PendingSubmission other : pending) {
            if (other.operationId() == candidate.id()) {
                continue;
            }
            if (other.status() == Status.SENDING || other.status() == Status.UNKNOWN_RESULT) {
                throw new SubmissionOrderConflictException(
                        "PRRO device has an unresolved submission " + other.operationId()
                                + "; reconcile it before sending another document");
            }
            if (other.localNumber() < candidate.localNumber()) {
                throw new SubmissionOrderConflictException(
                        "Earlier PRRO local number " + other.localNumber()
                                + " must be finalized before local number " + candidate.localNumber());
            }
        }
    }

    record PendingSubmission(long operationId, int localNumber, Status status) {
        PendingSubmission {
            Objects.requireNonNull(status, "status");
        }
    }

    public static class SubmissionOrderConflictException extends RuntimeException {
        public SubmissionOrderConflictException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public Operation get(long operationId) {
        return findById(operationId);
    }

    @Transactional(readOnly = true)
    public Operation findByIdempotencyKey(long deviceId, String idempotencyKey) {
        return findByIdempotencyKeyInternal(deviceId, idempotencyKey);
    }

    /**
     * Stores the exact unsigned XML bytes that the cashier must sign locally.
     * The write is one-time and only allowed while the operation is PREPARED.
     * Concurrent retries may read the already-stored identical payload.
     */
    @Transactional
    public PreparedSigningInput storeSigningInput(long operationId, byte[] signingInput,
                                                   String signingInputHash,
                                                   String previousDocumentHash,
                                                   long dpsDateTime) {
        if (signingInput == null || signingInput.length == 0) {
            throw new IllegalArgumentException("PRRO signing input must not be empty");
        }
        if (signingInputHash == null || !SHA_256.matcher(signingInputHash).matches()
                || !sha256(signingInput).equalsIgnoreCase(signingInputHash)) {
            throw new IllegalArgumentException("signingInputHash must match the SHA-256 of signingInput");
        }
        if (previousDocumentHash != null && !SHA_256.matcher(previousDocumentHash).matches()) {
            throw new IllegalArgumentException("previousDocumentHash must be a SHA-256 hex string");
        }
        if (dpsDateTime <= 0) {
            throw new IllegalArgumentException("dpsDateTime must be positive");
        }

        jdbcTemplate.update("""
                UPDATE prro_operations
                   SET signing_input = ?, signing_input_hash = ?,
                       previous_document_hash = ?, dps_date_time = ?
                 WHERE id = ? AND status = 'PREPARED' AND signing_input IS NULL
                """, signingInput, signingInputHash.toLowerCase(Locale.ROOT),
                previousDocumentHash == null ? null : previousDocumentHash.toLowerCase(Locale.ROOT),
                dpsDateTime, operationId);

        PreparedSigningInput stored = getPreparedSigningInput(operationId);
        if (stored == null) {
            throw new OperationStateConflictException(
                    "Unable to persist signing input for PRRO operation " + operationId);
        }
        // If another request with the same idempotency key won the race,
        // its first persisted payload is authoritative and must be reused.
        return stored;
    }

    @Transactional(readOnly = true)
    public PreparedSigningInput getPreparedSigningInput(long operationId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT signing_input, signing_input_hash, previous_document_hash, dps_date_time
                      FROM prro_operations
                     WHERE id = ?
                    """, (rs, rowNum) -> {
                byte[] bytes = rs.getBytes("signing_input");
                long dateTime = rs.getLong("dps_date_time");
                Long dateTimeValue = rs.wasNull() ? null : dateTime;
                return bytes == null || dateTimeValue == null ? null : new PreparedSigningInput(
                        bytes, rs.getString("signing_input_hash"),
                        rs.getString("previous_document_hash"), dateTimeValue);
            }, operationId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("PRRO operation not found: " + operationId, ex);
        }
    }

    /**
     * Returns the exact unsigned XML bytes originally supplied to IIT for a
     * fiscalized document. These bytes, rather than the signed CMS payload,
     * are the input to the DPS previous-document SHA-256 chain.
     *
     * @return a defensive copy, or {@code null} when the document is not a
     * locally persisted fiscalized operation
     */
    @Transactional(readOnly = true)
    public byte[] getFiscalizedSigningInput(long deviceId, String fiscalDocumentId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("deviceId must be positive");
        }
        if (fiscalDocumentId == null || fiscalDocumentId.isBlank()) {
            throw new IllegalArgumentException("fiscalDocumentId is required");
        }
        try {
            byte[] bytes = jdbcTemplate.queryForObject("""
                    SELECT signing_input
                      FROM prro_operations
                     WHERE device_id = ?
                       AND fiscal_document_id = ?
                       AND status = 'FISCALIZED'
                     ORDER BY local_number DESC, id DESC
                     LIMIT 1
                    """, (rs, rowNum) -> rs.getBytes("signing_input"),
                    deviceId, fiscalDocumentId);
            return bytes == null ? null : bytes.clone();
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public record PreparedSigningInput(byte[] bytes, String sha256,
                                       String previousDocumentHash, long dpsDateTime) {
        public PreparedSigningInput {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Signing input must not be empty");
            }
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private Device lockDevice(long deviceId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, enabled, active_shift_key, active_signer_key_id, next_local_number
                      FROM prro_devices
                     WHERE id = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new Device(
                    rs.getLong("id"),
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

    private Operation findByIdempotencyKeyInternal(long deviceId, String key) {
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
                || "TEST".equals(command.sourceType())
                || "SHIFT_OPEN".equals(command.sourceType())
                || "SHIFT_CLOSE".equals(command.sourceType())
                || "SERVICE".equals(command.sourceType());
        boolean supportedDocument = "CHK".equals(command.documentType())
                || "ZREPORT".equals(command.documentType())
                || "SERVICECHK".equals(command.documentType());
        if (!supportedSource || !supportedDocument) {
            throw new IllegalArgumentException("Unsupported PRRO source or document type");
        }
        if (("PAYMENT".equals(command.sourceType()) || "TEST".equals(command.sourceType()))
                && !"CHK".equals(command.documentType())) {
            throw new IllegalArgumentException("PAYMENT and TEST operations must use CHK documents");
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
            case PREPARED -> next == Status.SIGNED || next == Status.REJECTED
                    || next == Status.CANCELLED;
            case SIGNED -> next == Status.SENDING || next == Status.REJECTED;
            case SENDING -> next == Status.FISCALIZED || next == Status.REJECTED
                    || next == Status.UNKNOWN_RESULT;
            case UNKNOWN_RESULT -> next == Status.FISCALIZED || next == Status.REJECTED;
            case FISCALIZED, REJECTED, CANCELLED -> false;
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

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record Device(long id, boolean enabled, String activeShiftKey,
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
        PREPARED, SIGNED, SENDING, FISCALIZED, REJECTED, UNKNOWN_RESULT, CANCELLED
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
