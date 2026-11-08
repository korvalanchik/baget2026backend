package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.config.PrroProperties;
import com.programika.rro.ws.chk.CheckResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Stores the exact binary document payload returned by DPS for each accepted
 * PRRO operation. It deliberately does not decode, canonicalize, or rebuild
 * data_sign: the archived bytes are the source used for later reprinting.
 */
@Service
public class PrroFiscalDocumentArchiveService {
    private static final ZoneId UKRAINE = ZoneId.of("Europe/Kyiv");
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate db;
    private final PrroOperationService operations;
    private final PrroAccessService access;
    private final PrroProperties properties;

    public PrroFiscalDocumentArchiveService(JdbcTemplate db,
                                            PrroOperationService operations,
                                            PrroAccessService access,
                                            PrroProperties properties) {
        this.db = Objects.requireNonNull(db);
        this.operations = Objects.requireNonNull(operations);
        this.access = Objects.requireNonNull(access);
        this.properties = Objects.requireNonNull(properties);
    }

    /**
     * Completes a successful DPS submission and archives its original response
     * in the same database transaction. If the archive write fails, the caller
     * can safely mark the operation UNKNOWN_RESULT rather than claiming that a
     * printable local archive exists.
     */
    @Transactional
    public PrroOperationService.Operation completeFiscalized(
            long operationId, String signedDocumentHash, CheckResponse response) {
        Objects.requireNonNull(response, "response");
        if (response.getStatus() != CheckResponse.Status.OK) {
            throw new IllegalArgumentException("Only an OK DPS response can be archived as fiscalized");
        }
        if (response.getId() == null || response.getId().isBlank()) {
            throw new IllegalArgumentException("DPS did not return a fiscal document ID");
        }

        PrroOperationService.Operation before = operations.get(operationId);
        if (before.status() != PrroOperationService.Status.SENDING) {
            throw new IllegalStateException("Only a SENDING PRRO operation can be finalized");
        }

        DeviceSnapshot device = db.queryForObject("""
                SELECT d.id, d.branch_no, b.BranchName, d.rro_fn, d.display_name
                  FROM prro_devices d
                  LEFT JOIN branches b ON b.BranchNo = d.branch_no
                 WHERE d.id = ?
                """, (rs, rowNum) -> new DeviceSnapshot(
                rs.getLong("id"), rs.getLong("branch_no"), rs.getString("BranchName"),
                rs.getString("rro_fn"), rs.getString("display_name")), before.deviceId());

        PrroOperationService.PreparedSigningInput signingInput =
                operations.getPreparedSigningInput(operationId);
        byte[] dataSign = response.getDataSign().toByteArray();
        byte[] idSign = response.getIdSign().toByteArray();
        String dataSignHash = dataSign.length == 0 ? null : sha256(dataSign);
        String idSignHash = idSign.length == 0 ? null : sha256(idSign);
        String archiveStatus = dataSign.length == 0 ? "MISSING_DATA" : "CAPTURED";

        PrroOperationService.Operation fiscalized = operations.transition(
                operationId, PrroOperationService.Status.SENDING,
                PrroOperationService.Status.FISCALIZED, signedDocumentHash,
                response.getId(), response.getStatusValue(), response.getErrorMessage());

        db.update("""
                INSERT INTO prro_fiscal_documents
                    (operation_id, device_id, branch_no, branch_name, fiscal_number,
                     device_name, endpoint_mode, fiscal_document_id, document_type,
                     source_type, source_reference, local_number, response_code,
                     dps_date_time, data_sign, id_sign, data_sign_sha256, id_sign_sha256,
                     data_sign_length, id_sign_length, archive_status, archived_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                operationId, device.id(), device.branchNo(), safe(device.branchName()),
                device.fiscalNumber(), device.deviceName(), properties.getEndpointMode(),
                response.getId(), before.documentType(), before.sourceType(),
                before.sourceReference(), before.localNumber(), response.getStatusValue(),
                signingInput == null ? null : signingInput.dpsDateTime(),
                dataSign.length == 0 ? null : dataSign,
                idSign.length == 0 ? null : idSign,
                dataSignHash, idSignHash, dataSign.length, idSign.length,
                archiveStatus, before.createdBy());

        return fiscalized;
    }

    @Transactional(readOnly = true)
    public SearchResult search(long branchNo, LocalDate from, LocalDate to, String query,
                               int page, int size,
                               org.springframework.security.core.Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);
        if (branchNo <= 0) throw badRequest("Номер філії має бути додатним.");
        if (page < 0 || page > 100_000) throw badRequest("Некоректний номер сторінки.");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("Розмір сторінки має бути від 1 до " + MAX_PAGE_SIZE + ".");
        }

        LocalDate dateTo = to == null ? LocalDate.now(UKRAINE) : to;
        LocalDate dateFrom = from == null ? dateTo.minusDays(30) : from;
        if (dateFrom.isAfter(dateTo)) throw badRequest("Початкова дата більша за кінцеву.");
        if (dateFrom.isBefore(dateTo.minusYears(5))) {
            throw badRequest("Період пошуку не може перевищувати п'ять років.");
        }
        String search = normalizeQuery(query);
        Timestamp fromTimestamp = Timestamp.valueOf(dateFrom.atStartOfDay());
        Timestamp toExclusive = Timestamp.valueOf(dateTo.plusDays(1).atStartOfDay());
        int fetchSize = size + 1;
        long offset = (long) page * size;

        List<DocumentSummary> rows = db.query("""
                SELECT id, operation_id, branch_no, branch_name, fiscal_number, device_name,
                       endpoint_mode, fiscal_document_id, document_type, source_type,
                       source_reference, local_number, response_code, dps_date_time,
                       archive_status, data_sign_length, data_sign_sha256, archived_at
                  FROM prro_fiscal_documents
                 WHERE branch_no = ?
                   AND archived_at >= ?
                   AND archived_at < ?
                   AND (? IS NULL
                        OR fiscal_document_id LIKE CONCAT('%', ?, '%')
                        OR fiscal_number LIKE CONCAT('%', ?, '%')
                        OR device_name LIKE CONCAT('%', ?, '%')
                        OR source_reference LIKE CONCAT('%', ?, '%')
                        OR CAST(local_number AS CHAR) LIKE CONCAT('%', ?, '%'))
                 ORDER BY archived_at DESC, id DESC
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new DocumentSummary(
                rs.getLong("id"), rs.getLong("operation_id"), rs.getLong("branch_no"),
                rs.getString("branch_name"), rs.getString("fiscal_number"),
                rs.getString("device_name"), rs.getString("endpoint_mode"),
                rs.getString("fiscal_document_id"), rs.getString("document_type"),
                rs.getString("source_type"), rs.getString("source_reference"),
                rs.getInt("local_number"), rs.getInt("response_code"),
                nullableLong(rs.getObject("dps_date_time")),
                rs.getString("archive_status"), rs.getLong("data_sign_length"),
                rs.getString("data_sign_sha256"),
                rs.getTimestamp("archived_at").toInstant().toString()),
                branchNo, fromTimestamp, toExclusive, search, search, search, search, search,
                search, fetchSize, offset);

        boolean hasMore = rows.size() > size;
        List<DocumentSummary> pageRows = hasMore ? rows.subList(0, size) : rows;
        return new SearchResult(pageRows, page, size, hasMore, dateFrom, dateTo);
    }

    @Transactional(readOnly = true)
    public ArchivedDocument get(long documentId,
                                org.springframework.security.core.Authentication authentication) {
        if (documentId <= 0) throw badRequest("Некоректний ідентифікатор документа.");
        var user = access.requireOperationalUser(authentication);
        List<ArchivedDocument> rows = db.query("""
                SELECT id, operation_id, branch_no, branch_name, fiscal_number, device_name,
                       endpoint_mode, fiscal_document_id, document_type, source_type,
                       source_reference, local_number, response_code, dps_date_time,
                       archive_status, data_sign_length, data_sign_sha256, id_sign_length,
                       id_sign_sha256, data_sign, id_sign, archived_by, archived_at
                  FROM prro_fiscal_documents
                 WHERE id = ?
                """, (rs, rowNum) -> {
            byte[] dataSign = rs.getBytes("data_sign");
            byte[] idSign = rs.getBytes("id_sign");
            return new ArchivedDocument(
                    rs.getLong("id"), rs.getLong("operation_id"), rs.getLong("branch_no"),
                    rs.getString("branch_name"), rs.getString("fiscal_number"),
                    rs.getString("device_name"), rs.getString("endpoint_mode"),
                    rs.getString("fiscal_document_id"), rs.getString("document_type"),
                    rs.getString("source_type"), rs.getString("source_reference"),
                    rs.getInt("local_number"), rs.getInt("response_code"),
                    nullableLong(rs.getObject("dps_date_time")),
                    rs.getString("archive_status"), rs.getLong("data_sign_length"),
                    rs.getString("data_sign_sha256"), rs.getLong("id_sign_length"),
                    rs.getString("id_sign_sha256"),
                    dataSign == null ? null : Base64.getEncoder().encodeToString(dataSign),
                    idSign == null ? null : Base64.getEncoder().encodeToString(idSign),
                    rs.getString("archived_by"),
                    rs.getTimestamp("archived_at").toInstant().toString());
        }, documentId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Фіскальний документ в архіві не знайдено.");
        ArchivedDocument document = rows.get(0);
        access.requireBranchAccess(user, document.branchNo());
        return document;
    }

    private static String normalizeQuery(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 100) throw badRequest("Пошуковий запит не може бути довшим за 100 символів.");
        return result;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record DeviceSnapshot(long id, long branchNo, String branchName,
                                  String fiscalNumber, String deviceName) {}

    public record DocumentSummary(
            long id, long operationId, long branchNo, String branchName,
            String fiscalNumber, String deviceName, String endpointMode,
            String fiscalDocumentId, String documentType, String sourceType,
            String sourceReference, int localNumber, int responseCode,
            Long dpsDateTime, String archiveStatus, long dataSignLength,
            String dataSignSha256, String archivedAt) {}

    public record SearchResult(List<DocumentSummary> documents, int page, int size,
                               boolean hasMore, LocalDate dateFrom, LocalDate dateTo) {}

    public record ArchivedDocument(
            long id, long operationId, long branchNo, String branchName,
            String fiscalNumber, String deviceName, String endpointMode,
            String fiscalDocumentId, String documentType, String sourceType,
            String sourceReference, int localNumber, int responseCode,
            Long dpsDateTime, String archiveStatus, long dataSignLength,
            String dataSignSha256, long idSignLength, String idSignSha256,
            String dataSignBase64, String idSignBase64, String archivedBy,
            String archivedAt) {}
}
