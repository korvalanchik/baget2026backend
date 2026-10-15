package com.example.baget.assortment;

import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FrameAssortmentServiceTest {
    JdbcTemplate db;
    UsersRepository users;
    FrameAssortmentService service;
    User user;
    Authentication auth;

    @BeforeEach
    void setup() {
        db = mock(JdbcTemplate.class);
        users = mock(UsersRepository.class);
        service = new FrameAssortmentService(db, users);
        user = new User();
        var role = new Role();
        role.setName("ROLE_ADMIN");
        user.setRoles(List.of(role));
        user.setAllowedBranches(Set.of());
        auth = new UsernamePasswordAuthenticationToken("admin", "", List.of());
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(db.queryForList("SELECT BranchNo AS id,BranchName AS name FROM branches ORDER BY BranchName,BranchNo"))
                .thenReturn(List.of(Map.of("id", 2L, "name", "Branch")));
        when(db.queryForList(startsWith("SELECT p.PartNo FROM parts p"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(10L));
    }

    FrameAssortmentService.Change change(long revision, String status) {
        return new FrameAssortmentService.Change(revision, status, "Перевірено зразки");
    }

    private void setCourierWithAssignedBranch() {
        var role = new Role();
        role.setName("ROLE_COURIER");
        user.setRoles(List.of(role));
        var branch = new Branch();
        branch.setBranchNo(2L);
        branch.setName("Branch");
        user.setAllowedBranches(Set.of(branch));
    }

    @Test
    void emptySearchFromStringTrimmerDoesNotHideProfiles() {
        service.matrix(null, 0, false, auth);
        verify(db).queryForList(startsWith("SELECT p.PartNo AS partNo"), eq(""), eq(""), eq(0), eq(0));
    }

    @Test
    void initialSampleDoesNotTouchPhysicalStock() {
        service.sample(10, 2, change(0, "ACTIVE"), auth);
        verify(db).update(startsWith("INSERT INTO branch_frame_samples"), eq(10L), eq(2L),
                eq("ACTIVE"), eq(1L), eq("admin"), eq("Перевірено зразки"));
        verify(db, never()).update(startsWith("UPDATE parts"), any(Object[].class));
    }

    @Test
    void staleSampleCannotWrite() {
        when(db.queryForList("SELECT status,revision FROM branch_frame_samples WHERE part_no=? AND branch_no=?", 10L, 2L))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "revision", 1L)));
        assertThrows(PreviewException.class, () -> service.sample(10, 2, change(0, "REMOVED"), auth));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void roleCannotBypassBranchScope() {
        var role = new Role();
        role.setName("ROLE_FOREMAN");
        user.setRoles(List.of(role));
        assertThrows(PreviewException.class, () -> service.sample(10, 2, change(0, "ACTIVE"), auth));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void courierCanChangeSamplesInAnAssignedBranch() {
        setCourierWithAssignedBranch();
        service.sample(10, 2, change(0, "ACTIVE"), auth);
        verify(db).update(startsWith("INSERT INTO branch_frame_samples"), eq(10L), eq(2L),
                eq("ACTIVE"), eq(1L), eq("admin"), eq("Перевірено зразки"));
    }

    @Test
    void courierCannotChangeSamplesInAnUnassignedBranch() {
        setCourierWithAssignedBranch();
        assertThrows(PreviewException.class, () -> service.sample(10, 99, change(0, "ACTIVE"), auth));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void courierCannotChangeGlobalPurchasePolicyEvenIfAnotherNonAdminRoleIsAssigned() {
        var courier = new Role();
        courier.setName("ROLE_COURIER");
        var foreman = new Role();
        foreman.setName("ROLE_FOREMAN");
        user.setRoles(List.of(courier, foreman));

        assertThrows(PreviewException.class, () -> service.purchase(10, change(0, "BLOCKED"), auth));
        verifyNoInteractions(db);
    }

    @Test
    void unknownBranchAndInvalidStatusRejected() {
        assertThrows(PreviewException.class, () -> service.sample(10, 99, change(0, "ACTIVE"), auth));
        assertThrows(PreviewException.class, () -> service.sample(10, 2, change(0, "OTHER"), auth));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void databaseRoleRequired() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        assertThrows(PreviewException.class, () -> service.purchase(10, change(0, "BLOCKED"), auth));
        verifyNoInteractions(db);
    }

    @Test
    void purchasePolicyIsAuditedWithoutStockChanges() {
        service.purchase(10, change(0, "DISCONTINUED"), auth);
        verify(db).update(startsWith("INSERT INTO frame_assortment_events"), eq(10L), isNull(), isNull(),
                eq("DISCONTINUED"), eq(1L), eq("admin"), eq("Перевірено зразки"));
        verify(db, never()).update(startsWith("UPDATE parts"), any(Object[].class));
    }
}
