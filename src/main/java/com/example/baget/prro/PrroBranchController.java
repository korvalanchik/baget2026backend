package com.example.baget.prro;

import com.example.baget.branch.Branch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/prro/branches")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroBranchController {
    private final JdbcTemplate db;
    private final PrroAccessService access;

    public PrroBranchController(JdbcTemplate db, PrroAccessService access) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping
    public List<PrroAdminController.BranchView> branches(Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        if (access.isGlobalOperator(user)) {
            return db.query("SELECT BranchNo, BranchName FROM branches ORDER BY BranchNo",
                    (rs, row) -> new PrroAdminController.BranchView(rs.getLong("BranchNo"), rs.getString("BranchName")));
        }
        return user.getAllowedBranches().stream()
                .sorted(java.util.Comparator.comparing(Branch::getBranchNo))
                .map(branch -> new PrroAdminController.BranchView(branch.getBranchNo(), branch.getName()))
                .toList();
    }
}
