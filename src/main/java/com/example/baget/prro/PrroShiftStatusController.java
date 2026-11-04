package com.example.baget.prro;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Branch-scoped, read-only PRRO shift status for cashier workflows.
 *
 * The caller signs the fiscal number locally with IIT. Only the resulting
 * signature is sent to this endpoint; private key material never leaves the
 * cashier workstation.
 */
@RestController
@RequestMapping("/api/prro/shift")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroShiftStatusController {
    private final PrroAccessService access;
    private final JdbcTemplate db;
    private final DpsPrroGrpcClient client;

    public PrroShiftStatusController(PrroAccessService access, JdbcTemplate db,
                                     DpsPrroGrpcClient client) {
        this.access = Objects.requireNonNull(access, "access");
        this.db = Objects.requireNonNull(db, "db");
        this.client = Objects.requireNonNull(client, "client");
    }

    @PostMapping("/status")
    public ShiftStatusResponse status(
            @Valid @RequestBody ShiftStatusRequest request,
            Authentication authentication) {
        long branchNo = request.branchNo();
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);

        final byte[] signature;
        try {
            signature = Base64.getDecoder().decode(request.signatureBase64());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис ПРРО має бути коректним рядком Base64.", ex);
        }
        if (signature.length == 0 || signature.length > 64 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некоректний розмір підпису ПРРО.");
        }

        List<Device> devices = db.query("""
                SELECT id, rro_fn, display_name, environment, active_shift_key,
                       active_signer_key_id, next_local_number
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(
                rs.getLong("id"),
                rs.getString("rro_fn"),
                rs.getString("display_name"),
                rs.getString("environment"),
                rs.getString("active_shift_key"),
                rs.getString("active_signer_key_id"),
                rs.getInt("next_local_number")), branchNo);

        if (devices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Для цієї філії не налаштовано активний ПРРО.");
        }
        if (devices.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Для філії налаштовано кілька ПРРО. Потрібно визначити касу.");
        }

        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Фіскальний номер не відповідає ПРРО, налаштованому для цієї філії.");
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
                    "Не вдалося отримати стан ПРРО від ДПС.", ex);
        }

        boolean localShiftPrepared = device.activeShiftKey() != null
                && !device.activeShiftKey().isBlank();
        String activeSignerKeyId = device.activeSignerKeyId();
        boolean localSignerBound = activeSignerKeyId != null && !activeSignerKeyId.isBlank();
        boolean dpsInfoOk = info.getStatus() == RroInfoResponse.Status.OK;
        boolean dpsStatusOk = status.getStatus() == StatusResponse.Status.OK;
        boolean dpsSignerMatches = !status.getOpenShift()
                || localSignerBound
                && activeSignerKeyId.equalsIgnoreCase(status.getLastSigner())
                && info.getOperatorsList().stream().anyMatch(operator ->
                activeSignerKeyId.equalsIgnoreCase(operator.getSerial()) && operator.getStatus() == 1);
        boolean stateMatches = dpsInfoOk && dpsStatusOk
                && info.getOpenShift() == status.getOpenShift()
                && localShiftPrepared == status.getOpenShift()
                && dpsSignerMatches;

        List<OperatorView> operators = info.getOperatorsList().stream()
                .map(operator -> new OperatorView(operator.getSerial(), operator.getStatus(),
                        operator.getSenior(), operator.getIsname()))
                .toList();

        String state;
        if (!dpsInfoOk || !dpsStatusOk) {
            state = "DPS_ERROR";
        } else if (!stateMatches) {
            state = "RECONCILIATION_REQUIRED";
        } else {
            state = status.getOpenShift() ? "OPEN" : "CLOSED";
        }

        return new ShiftStatusResponse(
                branchNo,
                device.id(),
                device.rroFn(),
                device.displayName(),
                device.environment(),
                state,
                info.getStatus().name(),
                status.getStatus().name(),
                info.getStatusRro(),
                status.getOnline(),
                status.getOpenShift(),
                localShiftPrepared,
                localSignerBound,
                device.nextLocalNumber(),
                localSignerBound ? activeSignerKeyId : null,
                status.getLastSigner(),
                operators,
                status.getErrorMessage());
    }

    /**
     * Read-only diagnostic endpoint for the previous fiscal document.
     * DPS documents that lastChk returns the previous check in data_sign.
     * The digest is calculated directly from the raw data_sign bytes, without
     * decoding or reserializing the signed XML. Only metadata and the digest
     * are returned to the caller; the signed payload is not exposed by this
     * diagnostic endpoint.
     */
    @PostMapping("/last-check")
    public LastCheckResponse lastCheck(
            @Valid @RequestBody ShiftStatusRequest request,
            Authentication authentication) {
        long branchNo = request.branchNo();
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);

        final byte[] signature;
        try {
            signature = Base64.getDecoder().decode(request.signatureBase64());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис ПРРО має бути коректним рядком Base64.", ex);
        }
        if (signature.length == 0 || signature.length > 64 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некоректний розмір підпису ПРРО.");
        }

        List<Device> devices = db.query("""
                SELECT id, rro_fn, display_name, environment, active_shift_key,
                       active_signer_key_id, next_local_number
                  FROM prro_devices
                 WHERE branch_no = ? AND enabled = TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new Device(
                rs.getLong("id"),
                rs.getString("rro_fn"),
                rs.getString("display_name"),
                rs.getString("environment"),
                rs.getString("active_shift_key"),
                rs.getString("active_signer_key_id"),
                rs.getInt("next_local_number")), branchNo);

        if (devices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Для цієї філії не налаштовано активний ПРРО.");
        }
        if (devices.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Для філії налаштовано кілька ПРРО. Потрібно визначити касу.");
        }
        Device device = devices.get(0);
        if (!device.rroFn().equals(request.fiscalNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Фіскальний номер не відповідає ПРРО, налаштованому для цієї філії.");
        }

        CheckRequest signedRequest = CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(signature))
                .build();
        final CheckResponse response;
        try {
            response = client.getLastCheck(signedRequest);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не вдалося отримати останній чек ПРРО від ДПС.", ex);
        }

        byte[] previousCheck = response.getDataSign().toByteArray();
        return new LastCheckResponse(
                branchNo,
                device.id(),
                device.rroFn(),
                response.getStatusValue(),
                response.getStatus().name(),
                response.getId(),
                previousCheck.length,
                previousCheck.length > 0 && startsWithXml(previousCheck),
                previousCheck.length == 0 ? null : sha256(previousCheck),
                previousCheck.length == 0 ? null : Base64.getEncoder().encodeToString(previousCheck),
                response.getErrorMessage());
    }

    private static boolean startsWithXml(byte[] bytes) {
        byte[] prefix = "<?xml".getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record LastCheckResponse(
            long branchNo,
            long deviceId,
            String fiscalNumber,
            int responseCode,
            String responseStatus,
            String fiscalDocumentId,
            int dataSignLength,
            boolean dataSignStartsWithXml,
            String dataSignSha256,
            String dataSignBase64,
            String dpsMessage) {}

    public record ShiftStatusRequest(
            @Positive long branchNo,
            @NotBlank @Size(max = 32) String fiscalNumber,
            @NotBlank @Size(max = 100_000) String signatureBase64) {}

    private record Device(long id, String rroFn, String displayName, String environment,
                          String activeShiftKey, String activeSignerKeyId, int nextLocalNumber) {}

    public record OperatorView(String serial, int status, boolean senior, String name) {}

    public record ShiftStatusResponse(
            long branchNo,
            long deviceId,
            String fiscalNumber,
            String displayName,
            String environment,
            String state,
            String infoStatus,
            String statusStatus,
            int rroRegistrationStatus,
            boolean online,
            boolean dpsOpenShift,
            boolean localShiftPrepared,
            boolean localSignerBound,
            int nextLocalNumber,
            String activeSignerKeyId,
            String lastSigner,
            List<OperatorView> operators,
            String dpsMessage) {}
}
