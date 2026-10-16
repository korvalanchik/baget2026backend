package com.example.baget.assortment;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class FrameAssortmentService {
    private static final Set<String> SAMPLE_ROLES =
            Set.of("ROLE_ADMIN", "ROLE_FOREMAN", "ROLE_WAREHOUSE", "ROLE_COURIER");
    private static final Set<String> PURCHASE_ROLES =
            Set.of("ROLE_ADMIN", "ROLE_FOREMAN", "ROLE_WAREHOUSE");

    private final JdbcTemplate db;
    private final UsersRepository users;

    public FrameAssortmentService(JdbcTemplate db, UsersRepository users) {
        this.db = db;
        this.users = users;
    }

    public record Change(
            @PositiveOrZero long expectedRevision,
            @NotBlank String status,
            @NotBlank @Size(max = 1000) String note
    ) {}

    private Set<String> roleNames(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new PreviewException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Увійдіть до системи");
        }
        var user = users.findByUsername(authentication.getName())
                .orElseThrow(FrameAssortmentService::denied);
        var names = user.getRoles();
        if (names == null) return Set.of();
        var result = new HashSet<String>();
        names.forEach(role -> result.add(role.getName()));
        return result;
    }

    private boolean requireSampleAccess(Set<String> roleNames) {
        if (Collections.disjoint(roleNames, SAMPLE_ROLES)) throw denied();
        return roleNames.contains("ROLE_ADMIN");
    }

    private void requirePurchaseAccess(Set<String> roleNames) {
        // ROLE_COURIER is intentionally isolated from global procurement policy,
        // even if another non-admin role was accidentally assigned alongside it.
        if (roleNames.contains("ROLE_COURIER") && !roleNames.contains("ROLE_ADMIN")) throw denied();
        if (Collections.disjoint(roleNames, PURCHASE_ROLES)) throw denied();
    }

    private boolean courierOnly(Set<String> roleNames) {
        return roleNames.contains("ROLE_COURIER") && !roleNames.contains("ROLE_ADMIN");
    }

    private List<Map<String, Object>> branches(Authentication authentication) {
        Set<String> roleNames = roleNames(authentication);
        boolean admin = requireSampleAccess(roleNames);
        return branches(authentication, admin);
    }

    private List<Map<String, Object>> branches(Authentication authentication, boolean admin) {
        var rows = db.queryForList(
                "SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName,BranchNo");
        if (admin) return rows;

        var user = users.findByUsername(authentication.getName()).orElseThrow(FrameAssortmentService::denied);
        var allowedNames = user.getAllowedBranches() == null
                ? Set.<String>of()
                : user.getAllowedBranches().stream().map(b -> b.getName()).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(branch -> allowedNames.contains(branch.get("name"))).toList();
    }

    public static final String FRAME =
            "(p.calculation_method='FRAME_PROFILE' OR (p.InQuality=2 AND p.ProfilWidth>0.0003))";

    public Map<String, Object> matrix(String query, int page, boolean activeOnly, Authentication authentication) {
        query = query == null ? "" : query.trim();
        Set<String> roleNames = roleNames(authentication);
        boolean admin = requireSampleAccess(roleNames);
        boolean courier = courierOnly(roleNames);
        var branchRows = branches(authentication, admin);

        var allowedBranchIds = new LinkedHashSet<Long>();
        for (var branch : branchRows) allowedBranchIds.add(((Number) branch.get("id")).longValue());

        String activeFilter;
        var args = new ArrayList<Object>();
        args.add(query);
        args.add(query);
        if (courier && activeOnly) {
            if (allowedBranchIds.isEmpty()) {
                activeFilter = " AND 1=0";
            } else {
                activeFilter = " AND EXISTS (SELECT 1 FROM branch_frame_samples visible_sample " +
                        "WHERE visible_sample.part_no=p.PartNo AND visible_sample.status='ACTIVE' " +
                        "AND visible_sample.branch_no IN (" +
                        String.join(",", Collections.nCopies(allowedBranchIds.size(), "?")) + "))";
                args.addAll(allowedBranchIds);
            }
        } else {
            activeFilter = " AND (?=0 OR COALESCE(s.activeCount,0)>0)";
            args.add(activeOnly ? 1 : 0);
        }
        args.add(Math.max(0, Math.min(page, 100000)) * 50);

        var rows = db.queryForList(
                "SELECT p.PartNo AS partNo,p.Description AS description,p.calculation_method AS method," +
                        "p.unit_type AS unit,CAST(p.OnHand AS DECIMAL(24,6)) AS stock," +
                        "COALESCE(s.activeCount,0) AS activeCount," +
                        "COALESCE(f.status,'ALLOWED') AS purchaseStatus," +
                        "COALESCE(f.revision,0) AS purchaseRevision " +
                        "FROM parts p LEFT JOIN (SELECT part_no,COUNT(*) AS activeCount " +
                        "FROM branch_frame_samples WHERE status='ACTIVE' GROUP BY part_no) s ON s.part_no=p.PartNo " +
                        "LEFT JOIN frame_purchase_policy f ON f.part_no=p.PartNo WHERE " + FRAME +
                        " AND (LOCATE(?,p.Description)>0 OR LOCATE(?,CAST(p.PartNo AS CHAR))>0)" +
                        activeFilter + " ORDER BY p.PartNo LIMIT 50 OFFSET ?",
                args.toArray());

        if (!rows.isEmpty()) {
            var ids = rows.stream().map(row -> row.get("partNo")).toArray();
            var samples = db.queryForList(
                    "SELECT part_no AS partNo,branch_no AS branchNo,status,revision,note,actor," +
                            "updated_at AS updatedAt FROM branch_frame_samples WHERE part_no IN (" +
                            String.join(",", Collections.nCopies(ids.length, "?")) + ")",
                    ids);

            for (var row : rows) {
                var visibleSamples = samples.stream()
                        .filter(sample -> Objects.equals(sample.get("partNo"), row.get("partNo"))
                                && allowedBranchIds.contains(((Number) sample.get("branchNo")).longValue()))
                        .toList();
                row.put("samples", visibleSamples);

                if (courier) {
                    // Do not expose physical stock or the global purchase policy to couriers.
                    row.put("activeCount", visibleSamples.stream()
                            .filter(sample -> "ACTIVE".equals(sample.get("status"))).count());
                    row.remove("stock");
                    row.remove("purchaseStatus");
                    row.remove("purchaseRevision");
                }
            }
        }
        return Map.of("branches", branchRows, "rows", rows);
    }

    private void lockPart(long part) {
        if (db.queryForList("SELECT p.PartNo FROM parts p WHERE p.PartNo=? AND " + FRAME + " FOR UPDATE",
                Long.class, part).isEmpty()) throw conflict("Профіль не знайдено");
    }

    public Map<String, Object> sample(long part, long branch, Change request, Authentication authentication) {
        if (branches(authentication).stream()
                .noneMatch(row -> ((Number) row.get("id")).longValue() == branch)) throw denied();
        if (!Set.of("ACTIVE", "PAUSED", "REMOVED").contains(request.status()))
            throw conflict("Некоректний стан зразка");

        lockPart(part);
        var rows = db.queryForList(
                "SELECT status,revision FROM branch_frame_samples WHERE part_no=? AND branch_no=?", part, branch);
        String previous = check(rows, request.expectedRevision());
        db.update("INSERT INTO branch_frame_samples(part_no,branch_no,status,revision,actor,note) " +
                        "VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status)," +
                        "revision=VALUES(revision),actor=VALUES(actor),note=VALUES(note),updated_at=CURRENT_TIMESTAMP(6)",
                part, branch, request.status(), request.expectedRevision() + 1, authentication.getName(), request.note().trim());
        event(part, branch, previous, request, authentication);
        return Map.of("revision", request.expectedRevision() + 1);
    }

    public Map<String, Object> purchase(long part, Change request, Authentication authentication) {
        requirePurchaseAccess(roleNames(authentication));
        if (!Set.of("ALLOWED", "DISCONTINUED", "BLOCKED").contains(request.status()))
            throw conflict("Некоректний стан закупівлі");

        lockPart(part);
        var rows = db.queryForList("SELECT status,revision FROM frame_purchase_policy WHERE part_no=?", part);
        String previous = check(rows, request.expectedRevision());
        db.update("INSERT INTO frame_purchase_policy(part_no,status,revision,actor,note) VALUES(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE status=VALUES(status),revision=VALUES(revision),actor=VALUES(actor)," +
                        "note=VALUES(note),updated_at=CURRENT_TIMESTAMP(6)",
                part, request.status(), request.expectedRevision() + 1, authentication.getName(), request.note().trim());
        event(part, null, previous, request, authentication);
        return Map.of("revision", request.expectedRevision() + 1);
    }

    public List<Map<String, Object>> history(long part, Authentication authentication) {
        Set<String> roleNames = roleNames(authentication);
        boolean admin = requireSampleAccess(roleNames);
        boolean courier = courierOnly(roleNames);
        var branchRows = branches(authentication, admin);

        String scope = "";
        var args = new ArrayList<Object>();
        args.add(part);
        if (!admin) {
            if (courier) {
                // A courier must never see branch_no IS NULL events (global procurement policy).
                if (branchRows.isEmpty()) {
                    scope = " AND 1=0";
                } else {
                    scope = " AND e.branch_no IN (" +
                            String.join(",", Collections.nCopies(branchRows.size(), "?")) + ")";
                    for (var branch : branchRows) args.add(branch.get("id"));
                }
            } else {
                scope = " AND (e.branch_no IS NULL";
                for (var branch : branchRows) {
                    scope += " OR e.branch_no=?";
                    args.add(branch.get("id"));
                }
                scope += ")";
            }
        }
        return db.queryForList(
                "SELECT e.branch_no AS branchNo,b.BranchName AS branchName,e.previous_status AS previousStatus," +
                        "e.status,e.revision,e.actor,e.note,e.created_at AS createdAt " +
                        "FROM frame_assortment_events e LEFT JOIN branches b ON b.BranchNo=e.branch_no " +
                        "WHERE e.part_no=?" + scope + " ORDER BY e.id DESC LIMIT 200",
                args.toArray());
    }

    private String check(List<Map<String, Object>> rows, long expected) {
        long current = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("revision")).longValue();
        if (current != expected) throw conflict("Набір уже змінено. Оновіть таблицю");
        return rows.isEmpty() ? null : rows.get(0).get("status").toString();
    }

    private void event(long part, Long branch, String previous, Change request, Authentication authentication) {
        db.update("INSERT INTO frame_assortment_events(part_no,branch_no,previous_status,status,revision,actor,note) " +
                        "VALUES(?,?,?,?,?,?,?)",
                part, branch, previous, request.status(), request.expectedRevision() + 1,
                authentication.getName(), request.note().trim());
    }

    private static PreviewException denied() {
        return new PreviewException(HttpStatus.FORBIDDEN, "ASSORTMENT_ACCESS_DENIED",
                "Немає доступу до цієї філії або обліку зразків");
    }

    private static PreviewException conflict(String text) {
        return new PreviewException(HttpStatus.CONFLICT, "ASSORTMENT_CONFLICT", text);
    }
}
