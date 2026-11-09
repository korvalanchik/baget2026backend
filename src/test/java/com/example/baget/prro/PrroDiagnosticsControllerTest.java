package com.example.baget.prro;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.users.User;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrroDiagnosticsControllerTest {
    private DpsPrroGrpcClient client;
    private PrroDiagnosticsController controller;
    private PrroAccessService access;
    private User adminUser;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        client = mock(DpsPrroGrpcClient.class);
        access = mock(PrroAccessService.class);
        adminUser = mock(User.class);
        controller = new PrroDiagnosticsController(client, access);
        admin = mock(Authentication.class);
        when(access.requireOperationalUser(admin)).thenReturn(adminUser);
        when(access.isGlobalOperator(adminUser)).thenReturn(true);
    }

    @Test
    void decodesWidgetBase64AndCallsReadOnlyDpsMethods() {
        byte[] signedEnvelope = "cms-envelope".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(signedEnvelope);
        var info = RroInfoResponse.newBuilder()
                .setStatus(RroInfoResponse.Status.OK)
                .setStatusRro(1).setOpenShift(false).setOnline(true)
                .setName("Test RRO")
                .addOperators(RroInfoResponse.Operator.newBuilder()
                        .setSerial("serial-1").setStatus(1).setSenior(true).setIsname("Cashier"))
                .build();
        var status = StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                .setOpenShift(false).setOnline(true).build();
        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(info);
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(status);

        var result = controller.info(new PrroDiagnosticsController.InfoRequest(base64), admin);

        assertEquals("OK", result.infoStatus());
        assertEquals(1, result.statusRro());
        assertTrue(result.online());
        assertFalse(result.openShift());
        assertEquals("Cashier", result.operators().get(0).name());
        var captor = org.mockito.ArgumentCaptor.forClass(CheckRequest.class);
        verify(client).getRroInfo(captor.capture());
        assertEquals(ByteString.copyFrom(signedEnvelope), captor.getValue().getRroFnSign());
        verify(client).getRroStatus(captor.getValue());
    }

    @Test
    void lastCheckReturnsOnlyMetadataAndCallsReadOnlyLastChk() throws Exception {
        byte[] signedEnvelope = "cms-envelope".getBytes(StandardCharsets.UTF_8);
        byte[] dataSign = "binary-data-sign".getBytes(StandardCharsets.UTF_8);
        byte[] idSign = "binary-id-sign".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(signedEnvelope);
        var response = CheckResponse.newBuilder()
                .setId("P5lXLIFU4N0")
                .setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(dataSign))
                .setIdSign(ByteString.copyFrom(idSign))
                .build();
        when(client.getLastCheck(any(CheckRequest.class))).thenReturn(response);

        var result = controller.lastCheck(new PrroDiagnosticsController.InfoRequest(base64), admin);

        assertEquals("P5lXLIFU4N0", result.dpsDocumentId());
        assertEquals("OK", result.status());
        assertEquals(1, result.statusCode());
        assertEquals(dataSign.length, result.dataSignLength());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dataSign)),
                result.dataSignSha256());
        assertEquals(idSign.length, result.idSignLength());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(idSign)),
                result.idSignSha256());

        var captor = org.mockito.ArgumentCaptor.forClass(CheckRequest.class);
        verify(client).getLastCheck(captor.capture());
        assertEquals(ByteString.copyFrom(signedEnvelope), captor.getValue().getRroFnSign());
        verify(client, never()).sendCheckV2(any());
        verify(client, never()).deleteLastCheck(any());
        verify(client, never()).deleteLastCheckById(any());
    }

    @Test
    void refusesNonAdminBeforeCallingDps() {
        Authentication cashier = mock(Authentication.class);
        User cashierUser = mock(User.class);
        when(access.requireOperationalUser(cashier)).thenReturn(cashierUser);
        when(access.isGlobalOperator(cashierUser)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> controller.info(
                new PrroDiagnosticsController.InfoRequest("c2lnbg=="), cashier));
        assertThrows(ResponseStatusException.class, () -> controller.lastCheck(
                new PrroDiagnosticsController.InfoRequest("c2lnbg=="), cashier));
        verifyNoInteractions(client);
    }
}
