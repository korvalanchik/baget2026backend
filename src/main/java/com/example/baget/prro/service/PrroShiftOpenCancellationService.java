package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.users.User;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Cancels a local shift-open preparation only after DPS independently confirms
 * that the PRRO shift is still closed.
 *
 * A PREPARED operation has not advanced to SIGNED/SENDING and therefore has not
 * been submitted through the Baget sendChkV2 workflow. The operation is retained
 * as CANCELLED for audit; its local reservation is released in the same DB
 * transaction.
 */
@Service
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftOpenCancellationService {
    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;
    private final PrroOperationService operations;

    public PrroShiftOpenCancellationService(PrroAccessService access, JdbcTemplate db,
                                            DpsPrroGrpcClient client,
                                            PrroOperationService operations) {
        this.access = Objects.requireNonNull(access, "access");
        this.db = Objects.requireNonNull(db, "db");
        this.client = Objects.requireNonNull(client, "client");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @Transactional
    public CancellationResult cancel(CancelRequest request, Authentication authentication) {
        Objects.requireNonNull(request, "request");
        User user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, request.branchNo());

        byte[] signature = decodeSignature(request.signatureBase64());

        // Lock the device row before examining the reservation. This serializes
        // cancellation against another cancellation and against operation setup.
        List<Device> devices = db.query("""
                SELECT id, rro_fn, active_shift_key
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                 FOR UPDATE
                """, (rs, rowNum) -> new Device(
                rs.getLong("id"),
                rs.getString("rro_fn"),
                rs.getString("active_shift_key")), request.branchNo());

        if (devices.isEmpty()) {
            throw conflict("Для цієї філії не налаштовано активний ПРРО.");
        }
        if (devices.size() != 1) {
            throw conflict("Для філії налаштовано кілька ПРРО. Потрібно визначити касу.");
        }

        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw conflict("Фіскальний номер не відповідає ПРРО, налаштованому для цієї філії.");
        }
        if (device.activeShiftKey() == null || device.activeShiftKey().isBlank()) {
            throw conflict("Локальна резервація зміни вже відсутня. Оновіть стан ПРРО.");
        }

        List<PendingOpen> pending = db.query("""
                SELECT id, shift_key, status, signed_document_hash, fiscal_document_id
                  FROM prro_operations
                 WHERE device_id = ?
                   AND shift_key = ?
                   AND source_type = 'SHIFT_OPEN'
                   AND document_type = 'SERVICECHK'
                 ORDER BY id DESC
                 FOR UPDATE
                """, (rs, rowNum) -> new PendingOpen(
                rs.getLong("id"),
                rs.getString("shift_key"),
                rs.getString("status"),
                rs.getString("signed_document_hash"),
                rs.getString("fiscal_document_id")),
                device.id(), device.activeShiftKey());

        if (pending.size() != 1) {
            throw conflict("Для активної локальної резервації не знайдено одну однозначну операцію відкриття.");
        }

        PendingOpen operation = pending.get(0);
        if (!"PREPARED".equals(operation.status())) {
            throw conflict("Скасувати можна лише операцію відкриття у статусі PREPARED. "
                    + "Поточний статус: " + operation.status() + ".");
        }
        if (operation.signedDocumentHash() != null || operation.fiscalDocumentId() != null) {
            throw conflict("Операція містить ознаки підписаного або прийнятого документа. "
                    + "Автоматичне скасування заборонено.");
        }

        CheckRequest signedRequest = CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(signature))
                .build();

        final RroInfoResponse info;
        final StatusResponse status;
        try {
            info = client.getRroInfo(signedRequest);
            status = client.getRroStatus(signedRequest);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не вдалося повторно перевірити стан ПРРО в ДПС. Локальну резервацію не змінено.", ex);
        }

        if (info.getStatus() != RroInfoResponse.Status.OK
                || status.getStatus() != StatusResponse.Status.OK) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ДПС не підтвердила стан ПРРО. Локальну резервацію не змінено.");
        }
        if (info.getStatusRro() != 1) {
            throw conflict("ДПС не підтвердила активну реєстрацію ПРРО. Скасування заборонено.");
        }
        if (!info.getOnline() || !status.getOnline()) {
            throw conflict("ПРРО не перебуває онлайн. Неможливо безпечно підтвердити закриту зміну.");
        }
        if (info.getOpenShift() || status.getOpenShift()) {
            throw conflict("ДПС повідомляє, що зміна відкрита. Локальну операцію не скасовано.");
        }

        String auditMessage = "Локальну підготовку відкриття скасовано після повторної перевірки: "
                + "infoRro/statusRro підтвердили закриту зміну; SERVICECHK не надсилався.";
        PrroOperationService.Operation cancelled = operations.transition(
                operation.id(),
                PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.CANCELLED,
                null,
                null,
                null,
                auditMessage);

        return new CancellationResult(
                cancelled.id(),
                cancelled.status().name(),
                device.rroFn(),
                cancelled.shiftKey(),
                auditMessage);
    }

    private static byte[] decodeSignature(String encoded) {
        try {
            byte[] signature = Base64.getDecoder().decode(encoded);
            if (signature.length == 0 || signature.length > 64 * 1024) {
                throw new IllegalArgumentException("Invalid signature length");
            }
            return signature;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис фіскального номера ПРРО має бути коректним Base64 розміром до 64 КБ.", ex);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record CancelRequest(long branchNo, String fiscalNumber, String signatureBase64) {
        public CancelRequest {
            if (branchNo <= 0) {
                throw new IllegalArgumentException("branchNo must be positive");
            }
            if (fiscalNumber == null || fiscalNumber.isBlank() || fiscalNumber.length() > 32) {
                throw new IllegalArgumentException("fiscalNumber is required");
            }
            if (signatureBase64 == null || signatureBase64.isBlank()
                    || signatureBase64.length() > 100_000) {
                throw new IllegalArgumentException("signatureBase64 is required");
            }
        }
    }

    public record CancellationResult(long operationId, String status, String fiscalNumber,
                                     String shiftKey, String message) {}

    private record Device(long id, String rroFn, String activeShiftKey) {}

    private record PendingOpen(long id, String shiftKey, String status,
                               String signedDocumentHash, String fiscalDocumentId) {}
}
