package com.example.baget.workpricing;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkPriceServiceTest {
    private JdbcTemplate db;
    private UsersRepository users;
    private WorkPriceService service;
    private Role role;
    private final UsernamePasswordAuthenticationToken admin =
        new UsernamePasswordAuthenticationToken("admin", "", List.of());

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        users = mock(UsersRepository.class);
        User user = new User();
        role = new Role();
        role.setName("ROLE_ADMIN");
        user.setRoles(List.of(role));
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        service = new WorkPriceService(db, users);
    }

    private Map<String, Object> work(long version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partNo", 33370L);
        row.put("description", "Робота тест");
        row.put("cost", new BigDecimal("20.00"));
        row.put("listPrice", new BigDecimal("40.00"));
        row.put("listPrice1", new BigDecimal("41.00"));
        row.put("listPrice3", new BigDecimal("42.00"));
        row.put("version", version);
        return row;
    }

    @Test
    void requiresAdminBeforeReadingOrWriting() {
        role.setName("ROLE_USER");
        assertThrows(PreviewException.class, () -> service.list("", 0, admin));
        assertThrows(PreviewException.class,
            () -> service.update(33370, new WorkPriceService.PriceUpdate(0L,
                new BigDecimal("21.00"), null, null, null), admin));
        verifyNoInteractions(db);
    }

    @Test
    void listQueriesOnlyWorkItems() {
        when(db.queryForList(contains("LOCATE(LOWER(?)"), eq("робота"), eq("робота"), eq(0)))
            .thenReturn(List.of(work(3)));
        Map<String, Object> result = service.list("робота", 0, admin);
        assertEquals(1, ((List<?>) result.get("rows")).size());
        verify(db).queryForList(contains("p.part_kind = 'WORK'"), eq("робота"), eq("робота"), eq(0));
    }

    @Test
    void updatesOnlyProvidedPricesAndIncrementsVersion() {
        when(db.queryForList(contains("FOR UPDATE"), eq(33370L))).thenReturn(List.of(work(3)));
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.update(33370,
            new WorkPriceService.PriceUpdate(3L, new BigDecimal("22.50"), null, null, new BigDecimal("45.00")),
            admin);

        assertEquals(new BigDecimal("22.50"), result.get("cost"));
        assertEquals(new BigDecimal("45.00"), result.get("listPrice3"));
        assertEquals(new BigDecimal("40.00"), result.get("listPrice"));
        assertEquals(4L, result.get("version"));
        verify(db).update(
            eq("UPDATE parts SET Cost = ?, ListPrice_3 = ?, Version = COALESCE(Version, 0) + 1 WHERE PartNo = ? AND part_kind = 'WORK' AND COALESCE(Version, 0) = ?"),
            eq(new BigDecimal("22.50")), eq(new BigDecimal("45.00")), eq(33370L), eq(3L)
        );
    }

    @Test
    void rejectsStaleVersionBeforeWriting() {
        when(db.queryForList(contains("FOR UPDATE"), eq(33370L))).thenReturn(List.of(work(4)));
        assertThrows(PreviewException.class, () -> service.update(33370,
            new WorkPriceService.PriceUpdate(3L, new BigDecimal("22.50"), null, null, null), admin));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void refusesUpdateWhenEveryFieldIsBlank() {
        assertThrows(PreviewException.class, () -> service.update(33370,
            new WorkPriceService.PriceUpdate(0L, null, null, null, null), admin));
        verifyNoInteractions(db);
    }

    @Test
    void rejectsIdsThatAreNotWorkItems() {
        when(db.queryForList(contains("FOR UPDATE"), eq(33370L))).thenReturn(List.of());
        assertThrows(PreviewException.class, () -> service.update(33370,
            new WorkPriceService.PriceUpdate(0L, new BigDecimal("22.50"), null, null, null), admin));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
}
