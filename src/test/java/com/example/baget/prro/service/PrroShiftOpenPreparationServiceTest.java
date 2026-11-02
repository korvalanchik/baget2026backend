package com.example.baget.prro.service;

import com.example.baget.prro.PrroAccessService;
import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.protocol.PrroPreviousCheckHash;
import com.example.baget.users.User;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrroShiftOpenPreparationServiceTest {
    private PrroAccessService access;
    private JdbcTemplate db;
    private DpsPrroGrpcClient client;
    private PrroOperationService operations;
    private PrroShiftOpenPreparationService service;
    private Authentication authentication;
    private User user;
    private ResultSet deviceRow;

    @BeforeEach
    void setUp() throws Exception {
        access = mock(PrroAccessService.class);
        db = mock(JdbcTemplate.class);
        client = mock(DpsPrroGrpcClient.class);
        operations = mock(PrroOperationService.class);
        service = new PrroShiftOpenPreparationService(access, db, client, operations);
        authentication = mock(Authentication.class);
        user = mock(User.class);
        deviceRow = mock(ResultSet.class);
        when(access.requireOperationalUser(authentication)).thenReturn(user);
        when(authentication.getName()).thenReturn("cashier");
        when(deviceRow.getLong("id")).thenReturn(2L);
        when(deviceRow.getString("rro_fn")).thenReturn("4000318883");
        when(deviceRow.getString("environment")).thenReturn("TEST");
        when(deviceRow.getString("active_shift_key")).thenReturn(null);
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(deviceRow, 0));
        }).when(db).query(anyString(), any(RowMapper.class), eq(8L));
    }

    @Test
    void buildsAndPersistsShiftOpenEnvelopeWithHashOfPreviousSignedXml() throws Exception {
        byte[] previousSignedXml = ("<?xml version=\"1.0\" encoding=\"windows-1251\"?>"
                + "<RQ V=\"1\"><DAT><C T=\"108\"></C></DAT>"
                + "<Signature>signed-previous-document</Signature>")
                .getBytes(StandardCharsets.UTF_8);
        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(
                RroInfoResponse.newBuilder().setStatus(RroInfoResponse.Status.OK)
                        .setTins("2512702072").setOpenShift(false).setOnline(true).build());
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                        .setOpenShift(false).setOnline(true).build());
        when(client.getLastCheck(any(CheckRequest.class))).thenReturn(
                CheckResponse.newBuilder().setStatus(CheckResponse.Status.OK)
                        .setDataSign(ByteString.copyFrom(previousSignedXml)).build());
        when(operations.findByIdempotencyKey(2L, "open-shift-8-001")).thenReturn(null);

        String shiftKey = UUID.nameUUIDFromBytes(
                "baget-prro-shift-open:2:open-shift-8-001".getBytes(StandardCharsets.UTF_8)).toString();
        var operation = new PrroOperationService.Operation(
                101L, 2L, shiftKey, "open-shift-8-001", "a".repeat(64),
                "SHIFT_OPEN", null, "SERVICECHK", 0, "operator-cert-serial",
                PrroOperationService.Status.PREPARED, null, null, null, null, "cashier");
        when(operations.prepare(any(PrroOperationService.PrepareOperation.class))).thenReturn(operation);
        when(operations.storeSigningInput(eq(101L), any(byte[].class), anyString(),
                eq(PrroPreviousCheckHash.sha256Hex(previousSignedXml)), anyLong()))
                .thenAnswer(invocation -> new PrroOperationService.PreparedSigningInput(
                        invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4)));

        var result = service.prepare(new PrroShiftOpenPreparationService.PrepareRequest(
                8L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}),
                "operator-cert-serial", "open-shift-8-001"), authentication);

        assertEquals(101L, result.operationId());
        assertEquals(0, result.localNumber());
        assertEquals("PREPARED", result.status());
        assertEquals(14, Long.toString(result.dateTime()).length());
        assertEquals(PrroPreviousCheckHash.sha256Hex(previousSignedXml),
                result.previousDocumentSha256());
        String xml = new String(Base64.getDecoder().decode(result.signingInputBase64()),
                Charset.forName("windows-1251"));
        assertTrue(xml.contains("<MAC ID=\"\">" + result.previousDocumentSha256() + "</MAC>"));
        assertTrue(xml.contains("<C T=\"108\"></C>"));
        verify(client).getLastCheck(any(CheckRequest.class));
        verify(operations).storeSigningInput(eq(101L), any(byte[].class), anyString(),
                eq(result.previousDocumentSha256()), eq(result.dateTime()));
    }

    @Test
    void returnsStoredPreparationWithoutCallingDpsAgain() {
        when(deviceRow.getString("active_shift_key")).thenReturn("active-shift");
        String shiftKey = UUID.nameUUIDFromBytes(
                "baget-prro-shift-open:2:open-shift-8-002".getBytes(StandardCharsets.UTF_8)).toString();
        when(deviceRow.getString("active_shift_key")).thenReturn(shiftKey);
        String requestHash = sha256("8|2|4000318883|operator-cert-serial");
        var operation = new PrroOperationService.Operation(
                102L, 2L, shiftKey, "open-shift-8-002", requestHash,
                "SHIFT_OPEN", null, "SERVICECHK", 0, "operator-cert-serial",
                PrroOperationService.Status.PREPARED, null, null, null, null, "cashier");
        byte[] savedXml = "<?xml version=\"1.0\"?><RQ/>".getBytes(StandardCharsets.UTF_8);
        var saved = new PrroOperationService.PreparedSigningInput(
                savedXml, "c".repeat(64), null, 20261102120500L);
        when(operations.findByIdempotencyKey(2L, "open-shift-8-002")).thenReturn(operation);
        when(operations.getPreparedSigningInput(102L)).thenReturn(saved);

        var result = service.prepare(new PrroShiftOpenPreparationService.PrepareRequest(
                8L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1}),
                "operator-cert-serial", "open-shift-8-002"), authentication);

        assertEquals(102L, result.operationId());
        assertEquals(Base64.getEncoder().encodeToString(savedXml), result.signingInputBase64());
        assertEquals(20261102120500L, result.dateTime());
        verifyNoInteractions(client);
        verify(operations, never()).prepare(any());
    }
}
