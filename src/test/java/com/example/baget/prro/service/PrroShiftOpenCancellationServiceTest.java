package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.users.User;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrroShiftOpenCancellationServiceTest {
    private PrroAccessService access;
    private JdbcTemplate db;
    private DpsPrroGrpcClient client;
    private PrroOperationService operations;
    private PrroShiftOpenCancellationService service;
    private Authentication authentication;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        access = mock(PrroAccessService.class);
        db = mock(JdbcTemplate.class);
        client = mock(DpsPrroGrpcClient.class);
        operations = mock(PrroOperationService.class);
        authentication = mock(Authentication.class);
        user = mock(User.class);
        when(access.requireOperationalUser(authentication)).thenReturn(user);
        service = new PrroShiftOpenCancellationService(access, db, client, operations);
        stubPendingPreparation("shift-pending", "PREPARED", null, null);

        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(
                RroInfoResponse.newBuilder()
                        .setStatus(RroInfoResponse.Status.OK)
                        .setStatusRro(1)
                        .setOpenShift(false)
                        .setOnline(true)
                        .build());
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder()
                        .setStatus(StatusResponse.Status.OK)
                        .setOpenShift(false)
                        .setOnline(true)
                        .build());
        when(operations.transition(eq(42L),
                eq(PrroOperationService.Status.PREPARED),
                eq(PrroOperationService.Status.CANCELLED),
                isNull(), isNull(), isNull(), anyString()))
                .thenReturn(new PrroOperationService.Operation(
                        42L, 2L, "shift-pending", "idempotency-42", "a".repeat(64),
                        "SHIFT_OPEN", null, "SERVICECHK", 0, "operator-serial",
                        PrroOperationService.Status.CANCELLED, null, null, null,
                        "Cancelled locally before DPS submission", "cashier"));
    }

    @Test
    void cancelsOnlyPreparedOpeningAfterBothDpsStatusCallsConfirmClosedShift() {
        var result = service.cancel(new PrroShiftOpenCancellationService.CancelRequest(
                8L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})),
                authentication);

        assertEquals(42L, result.operationId());
        assertEquals("CANCELLED", result.status());
        assertEquals("4000318883", result.fiscalNumber());
        verify(access).requireBranchAccess(user, 8L);
        verify(client).getRroInfo(any(CheckRequest.class));
        verify(client).getRroStatus(any(CheckRequest.class));
        verify(operations).transition(eq(42L),
                eq(PrroOperationService.Status.PREPARED),
                eq(PrroOperationService.Status.CANCELLED),
                isNull(), isNull(), isNull(), contains("SERVICECHK не надсилався"));
    }

    @Test
    void refusesCancellationWhenDpsReportsOpenShift() {
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder()
                        .setStatus(StatusResponse.Status.OK)
                        .setOpenShift(true)
                        .setOnline(true)
                        .build());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancel(new PrroShiftOpenCancellationService.CancelRequest(
                        8L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1})),
                        authentication));

        assertEquals(409, error.getStatusCode().value());
        verify(operations, never()).transition(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refusesCancellationForAnOperationThatAlreadyAdvancedFromPrepared() {
        stubPendingPreparation("shift-pending", "SIGNED", "a".repeat(64), null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancel(new PrroShiftOpenCancellationService.CancelRequest(
                        8L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1})),
                        authentication));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(client);
        verify(operations, never()).transition(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubPendingPreparation(String activeShiftKey, String status,
                                         String signedHash, String fiscalDocumentId) {
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(2L);
            when(rs.getString("rro_fn")).thenReturn("4000318883");
            when(rs.getString("active_shift_key")).thenReturn(activeShiftKey);
            return List.of(mapper.mapRow(rs, 0));
        }).when(db).query(contains("FROM prro_devices"), any(RowMapper.class), eq(8L));

        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(42L);
            when(rs.getString("shift_key")).thenReturn(activeShiftKey);
            when(rs.getString("status")).thenReturn(status);
            when(rs.getString("signed_document_hash")).thenReturn(signedHash);
            when(rs.getString("fiscal_document_id")).thenReturn(fiscalDocumentId);
            return List.of(mapper.mapRow(rs, 0));
        }).when(db).query(contains("FROM prro_operations"), any(RowMapper.class),
                eq(2L), eq(activeShiftKey));
    }
}
