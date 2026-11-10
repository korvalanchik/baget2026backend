package com.example.baget.company;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyDetailsAdminService {

    private static final long COMPANY_DETAILS_ID = 1L;

    private final JdbcTemplate db;

    @Transactional(readOnly = true)
    public CompanyDetailsAdminDTO get() {
        return db.query("""
                SELECT full_name, edrpou, ipn, address
                  FROM company_details
                 WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Не знайдено запис реквізитів суб'єкта господарювання.");
            }
            return toDto(rs.getString("full_name"), rs.getString("edrpou"),
                    rs.getString("ipn"), rs.getString("address"));
        }, COMPANY_DETAILS_ID);
    }

    @Transactional
    public CompanyDetailsAdminDTO update(CompanyDetailsAdminRequest request) {
        db.update("""
                UPDATE company_details
                   SET full_name = ?, edrpou = ?, ipn = ?, address = ?
                 WHERE id = ?
                """,
                normalize(request.fullName()),
                normalize(request.edrpou()),
                normalize(request.ipn()),
                normalize(request.address()),
                COMPANY_DETAILS_ID);

        return get();
    }

    private CompanyDetailsAdminDTO toDto(String fullName, String edrpou, String ipn, String address) {
        List<String> warnings = new ArrayList<>();
        if (fullName == null || fullName.isBlank()) {
            warnings.add("Не вказано повне найменування суб'єкта господарювання.");
        }
        boolean hasIpn = ipn != null && !ipn.isBlank();
        boolean hasEdrpou = edrpou != null && !edrpou.isBlank();
        if (!hasIpn && !hasEdrpou) {
            warnings.add("Не вказано податковий номер: заповніть ЄДРПОУ / РНОКПП або ІПН платника ПДВ.");
        }
        return new CompanyDetailsAdminDTO(fullName, edrpou, ipn, address,
                warnings.isEmpty(), List.copyOf(warnings));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
