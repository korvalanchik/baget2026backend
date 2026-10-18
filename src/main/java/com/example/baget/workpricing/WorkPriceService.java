package com.example.baget.workpricing;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.UsersRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class WorkPriceService {
    private static final String COLUMNS = """
        SELECT p.PartNo AS partNo,
               p.Description AS description,
               CAST(p.Cost AS DECIMAL(24,2)) AS cost,
               CAST(p.ListPrice AS DECIMAL(24,2)) AS listPrice,
               CAST(p.ListPrice_1 AS DECIMAL(24,2)) AS listPrice1,
               CAST(p.ListPrice_3 AS DECIMAL(24,2)) AS listPrice3,
               COALESCE(p.Version, 0) AS version
        FROM parts p
        WHERE p.part_kind = 'WORK'
        """;

    private final JdbcTemplate db;
    private final UsersRepository users;

    public WorkPriceService(JdbcTemplate db, UsersRepository users) {
        this.db = db;
        this.users = users;
    }

    public record PriceUpdate(
        @NotNull @PositiveOrZero Long version,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal cost,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal listPrice,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal listPrice1,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal listPrice3
    ) {}

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !users.findByUsername(authentication.getName())
                .map(user -> user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName())))
                .orElse(false)) {
            throw new PreviewException(HttpStatus.FORBIDDEN, "WORK_PRICE_ACCESS",
                "Редагування цін робіт доступне лише адміністратору");
        }
    }

    public Map<String, Object> list(String query, int page, Authentication authentication) {
        requireAdmin(authentication);
        String search = query == null ? "" : query.trim();
        if (search.length() > 100) search = search.substring(0, 100);
        int offset = Math.max(0, Math.min(page, 100_000)) * 100;
        List<Map<String, Object>> rows = db.queryForList(
            COLUMNS + """
                 AND (LOCATE(LOWER(?), LOWER(p.Description)) > 0
                      OR LOCATE(?, CAST(p.PartNo AS CHAR)) > 0)
                 ORDER BY p.PartNo
                 LIMIT 100 OFFSET ?
                """,
            search, search, offset
        );
        return Map.of("rows", rows);
    }

    public Map<String, Object> update(long partNo, PriceUpdate request, Authentication authentication) {
        requireAdmin(authentication);
        if (request.cost() == null && request.listPrice() == null
            && request.listPrice1() == null && request.listPrice3() == null) {
            throw new PreviewException(HttpStatus.BAD_REQUEST, "WORK_PRICE_EMPTY",
                "Змініть хоча б одне поле ціни. Порожнє поле не змінює збережене значення.");
        }

        List<Map<String, Object>> found = db.queryForList(COLUMNS + " AND p.PartNo = ? FOR UPDATE", partNo);
        if (found.isEmpty()) {
            throw new PreviewException(HttpStatus.NOT_FOUND, "WORK_PRICE_NOT_FOUND",
                "Роботу не знайдено або вона не має типу WORK");
        }

        Map<String, Object> current = found.get(0);
        long currentVersion = ((Number) current.get("version")).longValue();
        if (!request.version().equals(currentVersion)) {
            throw new PreviewException(HttpStatus.CONFLICT, "WORK_PRICE_CHANGED",
                "Ціни роботи змінилися в іншій сесії. Оновіть список і повторіть зміну.");
        }

        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addChange(assignments, values, "Cost", request.cost());
        addChange(assignments, values, "ListPrice", request.listPrice());
        addChange(assignments, values, "ListPrice_1", request.listPrice1());
        addChange(assignments, values, "ListPrice_3", request.listPrice3());
        assignments.add("Version = COALESCE(Version, 0) + 1");
        values.add(partNo);
        values.add(currentVersion);

        int affected = db.update(
            "UPDATE parts SET " + String.join(", ", assignments)
                + " WHERE PartNo = ? AND part_kind = 'WORK' AND COALESCE(Version, 0) = ?",
            values.toArray()
        );
        if (affected != 1) {
            throw new PreviewException(HttpStatus.CONFLICT, "WORK_PRICE_CHANGED",
                "Ціни роботи змінилися в іншій сесії. Оновіть список і повторіть зміну.");
        }

        Map<String, Object> updated = new LinkedHashMap<>(current);
        if (request.cost() != null) updated.put("cost", request.cost());
        if (request.listPrice() != null) updated.put("listPrice", request.listPrice());
        if (request.listPrice1() != null) updated.put("listPrice1", request.listPrice1());
        if (request.listPrice3() != null) updated.put("listPrice3", request.listPrice3());
        updated.put("version", currentVersion + 1);
        return updated;
    }

    private void addChange(List<String> assignments, List<Object> values, String column, BigDecimal value) {
        if (value != null) {
            assignments.add(column + " = ?");
            values.add(value);
        }
    }
}
