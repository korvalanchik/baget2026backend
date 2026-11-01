package com.example.baget.prro;

import com.example.baget.branch.Branch;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Set;

@Service
public class PrroAccessService {
    private static final Set<String> OPERATIONAL_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SENIOR_CASHIER", "ROLE_CASHIER",
            "ROLE_MANAGER", "ROLE_SELLER", "ROLE_LEVEL2",
            "ROLE_COUNTER", "ROLE_ACCOUNTANT", "ROLE_BOOKKEEPER"
    );

    private final UsersRepository users;
    private final JdbcTemplate db;

    public PrroAccessService(UsersRepository users, JdbcTemplate db) {
        this.users = Objects.requireNonNull(users);
        this.db = Objects.requireNonNull(db);
    }

    public User requireOperationalUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw denied();
        }
        User user = users.findByUsername(authentication.getName())
                .orElseThrow(PrroAccessService::denied);
        if (user.getRoles() == null || user.getRoles().stream()
                .noneMatch(role -> OPERATIONAL_ROLES.contains(role.getName()))) {
            throw denied();
        }
        return user;
    }

    public void requireBranchAccess(User user, long branchNo) {
        if (isGlobalOperator(user)) return;
        boolean allowed = user.getAllowedBranches() != null
                && user.getAllowedBranches().stream()
                .map(Branch::getBranchNo)
                .anyMatch(number -> number != null && number == branchNo);
        if (!allowed) throw denied();
    }


    public void requireCustomerInBranch(long customerId, long branchNo) {
        Long count = db.queryForObject("""
                SELECT COUNT(*) FROM customer c
                 WHERE c.CustNo = ?
                   AND (EXISTS (SELECT 1 FROM orders o
                                 WHERE o.CustNo = c.CustNo AND o.BranchNo = ?)
                     OR EXISTS (SELECT 1 FROM invoices i
                                JOIN invoice_orders io ON io.invoice_id = i.id
                                JOIN orders o ON o.OrderNo = io.order_no
                                WHERE (i.customer_id = c.CustNo OR i.payer_id = c.CustNo)
                                  AND o.BranchNo = ?)
                     OR EXISTS (SELECT 1 FROM customer_transactions ct
                                WHERE ct.customer_id = c.CustNo AND ct.branch_id = ? AND ct.active = 1)
                     OR EXISTS (SELECT 1 FROM partner_customer_movements pm
                                WHERE pm.customer_id = c.CustNo AND pm.branch_no = ?))
                """, Long.class, customerId, branchNo, branchNo, branchNo, branchNo);
        if (count == null || count == 0) throw denied();
    }

    public void requireAdministrator(Authentication authentication) {
        User user = requireOperationalUser(authentication);
        if (!hasRole(user, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Керування налаштуваннями ПРРО доступне лише адміністратору.");
        }
    }

    public boolean isGlobalOperator(User user) {
        return hasRole(user, "ROLE_ADMIN") || hasRole(user, "ROLE_SENIOR_CASHIER");
    }

    private boolean hasRole(User user, String name) {
        return user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> name.equals(role.getName()));
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Немає доступу до цієї каси ПРРО або філії.");
    }
}
