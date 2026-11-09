package com.example.baget.prro;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/prro/receipt-details")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroReceiptDetailsController {
    private final JdbcTemplate db;
    private final PrroAccessService access;

    public PrroReceiptDetailsController(JdbcTemplate db, PrroAccessService access) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping("/{branchNo}")
    public ReceiptDetails get(@PathVariable long branchNo, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);

        List<ReceiptDetails> rows = db.query("""
                SELECT b.BranchName, b.address AS branch_address,
                       c.full_name, c.edrpou, c.ipn
                  FROM branches b
                  JOIN company_details c ON c.id = 1
                 WHERE b.BranchNo = ? AND b.active = TRUE
                """, (rs, row) -> {
            String ipn = rs.getString("ipn");
            boolean vatPayer = ipn != null && !ipn.isBlank();
            String taxId = vatPayer ? ipn : rs.getString("edrpou");
            return new ReceiptDetails(
                    rs.getString("full_name"),
                    vatPayer ? "ПН" : "ІД",
                    taxId,
                    rs.getString("BranchName"),
                    rs.getString("branch_address"));
        }, branchNo);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Не знайдено реквізити підприємства або активну господарську одиницю.");
        }
        ReceiptDetails details = rows.get(0);
        if (details.companyName() == null || details.companyName().isBlank()
                || details.taxId() == null || details.taxId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заповніть найменування суб'єкта господарювання та його податковий номер у company_details.");
        }
        return details;
    }

    public record ReceiptDetails(
            String companyName,
            String taxIdLabel,
            String taxId,
            String branchName,
            String branchAddress) {}
}
