package com.example.baget.prro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/prro/admin")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroAdminController {
    private final JdbcTemplate db;
    private final PrroAccessService access;

    public PrroAdminController(JdbcTemplate db, PrroAccessService access) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping("/branches")
    public List<BranchView> branches(Authentication authentication) {
        access.requireAdministrator(authentication);
        return db.query("""
                SELECT BranchNo, BranchName
                  FROM branches
                 ORDER BY BranchNo
                """, (rs, row) -> new BranchView(rs.getLong("BranchNo"),
                rs.getString("BranchName")));
    }

    @GetMapping("/devices")
    public List<DeviceView> devices(Authentication authentication) {
        access.requireAdministrator(authentication);
        return db.query("""
                SELECT d.id, d.branch_no, b.BranchName, d.rro_fn, d.display_name,
                       d.environment, d.enabled, d.active_shift_key,
                       d.active_signer_key_id, d.next_local_number,
                       d.created_at, d.updated_at
                  FROM prro_devices d
                  LEFT JOIN branches b ON b.BranchNo = d.branch_no
                 ORDER BY d.branch_no IS NULL, d.branch_no, d.id
                """, (rs, row) -> new DeviceView(
                rs.getLong("id"),
                (Long) rs.getObject("branch_no"),
                rs.getString("BranchName"),
                rs.getString("rro_fn"),
                rs.getString("display_name"),
                rs.getString("environment"),
                rs.getBoolean("enabled"),
                rs.getString("active_shift_key") != null,
                rs.getString("active_signer_key_id"),
                rs.getInt("next_local_number"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString()));
    }

    @PostMapping("/devices")
    @Transactional
    public DeviceView create(@Valid @RequestBody DeviceRequest request,
                             Authentication authentication) {
        access.requireAdministrator(authentication);
        validateRequest(request);
        ensureBranchAvailable(request.branchNo(), null);
        db.update("""
                INSERT INTO prro_devices
                    (branch_no, rro_fn, display_name, environment, enabled)
                VALUES (?, ?, ?, ?, ?)
                """, request.branchNo(), request.fiscalNumber().trim(),
                request.displayName().trim(), request.environment(), request.enabled());
        long id = Objects.requireNonNull(db.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class));
        audit(id, authentication.getName(), "CREATED");
        return device(id);
    }

    @PutMapping("/devices/{id}")
    @Transactional
    public DeviceView update(@PathVariable @Positive long id,
                             @Valid @RequestBody DeviceRequest request,
                             Authentication authentication) {
        access.requireAdministrator(authentication);
        validateRequest(request);
        DeviceState current = state(id);
        boolean identityChanged = !current.fiscalNumber().equals(request.fiscalNumber().trim())
                || !Objects.equals(current.branchNo(), request.branchNo())
                || !current.environment().equals(request.environment());
        if (identityChanged && current.activeShift()) {
            throw conflict("Не можна змінювати фіскальний номер, філію або середовище під час відкритої зміни.");
        }
        if (!request.enabled() && current.activeShift()) {
            throw conflict("Спочатку закрийте зміну ПРРО, а потім вимикайте пристрій.");
        }
        if (identityChanged) {
            Integer unresolved = db.queryForObject("""
                    SELECT COUNT(*) FROM prro_operations
                     WHERE device_id = ? AND status IN ('SENDING', 'UNKNOWN_RESULT')
                    """, Integer.class, id);
            if (unresolved != null && unresolved > 0) {
                throw conflict("Є операції з невідомим результатом. Спочатку звірте їх із ДПС.");
            }
        }
        ensureBranchAvailable(request.branchNo(), id);
        db.update("""
                UPDATE prro_devices
                   SET branch_no = ?, rro_fn = ?, display_name = ?,
                       environment = ?, enabled = ?
                 WHERE id = ?
                """, request.branchNo(), request.fiscalNumber().trim(),
                request.displayName().trim(), request.environment(), request.enabled(), id);
        audit(id, authentication.getName(), "UPDATED");
        return device(id);
    }

    @GetMapping("/audit")
    public List<AuditView> audit(Authentication authentication) {
        access.requireAdministrator(authentication);
        return db.query("""
                SELECT id, device_id, actor, action, snapshot, created_at
                  FROM prro_device_audit
                 ORDER BY id DESC
                 LIMIT 200
                """, (rs, row) -> new AuditView(rs.getLong("id"),
                (Long) rs.getObject("device_id"), rs.getString("actor"),
                rs.getString("action"), rs.getString("snapshot"),
                rs.getTimestamp("created_at").toInstant().toString()));
    }

    private void validateRequest(DeviceRequest request) {
        if (!"TEST".equals(request.environment()) && !"PRODUCTION".equals(request.environment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Середовище має бути TEST або PRODUCTION.");
        }
        if (request.branchNo() != null) {
            Integer exists = db.queryForObject(
                    "SELECT COUNT(*) FROM branches WHERE BranchNo = ?",
                    Integer.class, request.branchNo());
            if (exists == null || exists == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вказану філію не знайдено.");
            }
        }
        if (request.enabled() && request.branchNo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для активації ПРРО потрібно прив'язати його до філії.");
        }
    }

    private void ensureBranchAvailable(Long branchNo, Long exceptId) {
        if (branchNo == null) return;
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM prro_devices
                 WHERE branch_no = ? AND (? IS NULL OR id <> ?)
                """, Integer.class, branchNo, exceptId, exceptId);
        if (count != null && count > 0) {
            throw conflict("Для цієї філії вже налаштовано ПРРО. На першому етапі дозволено один пристрій на філію.");
        }
    }

    private DeviceState state(long id) {
        List<DeviceState> rows = db.query("""
                SELECT branch_no, rro_fn, environment, active_shift_key
                  FROM prro_devices WHERE id = ?
                """, (rs, row) -> new DeviceState((Long) rs.getObject("branch_no"),
                rs.getString("rro_fn"), rs.getString("environment"),
                rs.getString("active_shift_key") != null), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ПРРО не знайдено.");
        return rows.get(0);
    }

    private DeviceView device(long id) {
        return db.queryForObject("""
                SELECT d.id, d.branch_no, b.BranchName, d.rro_fn, d.display_name,
                       d.environment, d.enabled, d.active_shift_key,
                       d.active_signer_key_id, d.next_local_number,
                       d.created_at, d.updated_at
                  FROM prro_devices d LEFT JOIN branches b ON b.BranchNo = d.branch_no
                 WHERE d.id = ?
                """, (rs, row) -> new DeviceView(rs.getLong("id"),
                (Long) rs.getObject("branch_no"), rs.getString("BranchName"),
                rs.getString("rro_fn"), rs.getString("display_name"),
                rs.getString("environment"), rs.getBoolean("enabled"),
                rs.getString("active_shift_key") != null,
                rs.getString("active_signer_key_id"), rs.getInt("next_local_number"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString()), id);
    }

    private void audit(long id, String actor, String action) {
        DeviceView view = device(id);
        String snapshot = "{\"branchNo\":" + (view.branchNo() == null ? "null" : view.branchNo())
                + ",\"fiscalNumber\":\"" + escape(view.fiscalNumber())
                + "\",\"displayName\":\"" + escape(view.displayName())
                + "\",\"environment\":\"" + view.environment()
                + "\",\"enabled\":" + view.enabled() + "}";
        db.update("""
                INSERT INTO prro_device_audit (device_id, actor, action, snapshot)
                VALUES (?, ?, ?, CAST(? AS JSON))
                """, id, actor, action, snapshot);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record BranchView(long branchNo, String name) {}
    public record DeviceView(long id, Long branchNo, String branchName,
                             String fiscalNumber, String displayName, String environment,
                             boolean enabled, boolean shiftOpen, String signerKeyId,
                             int nextLocalNumber, String createdAt, String updatedAt) {}
    public record AuditView(long id, Long deviceId, String actor, String action,
                            String snapshot, String createdAt) {}
    public record DeviceRequest(Long branchNo,
            @NotBlank @Size(max = 32) @Pattern(regexp = "[0-9]+") String fiscalNumber,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank String environment, boolean enabled) {}
    private record DeviceState(Long branchNo, String fiscalNumber, String environment,
                               boolean activeShift) {}
}
