package com.example.baget.prro;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrroDiagnosticsControllerTest {
    private DpsPrroGrpcClient client;
    private PrroDiagnosticsController controller;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        client = mock(DpsPrroGrpcClient.class);
        controller = new PrroDiagnosticsController(client);
        admin = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();
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

        var result = controller.info(new PrroDiagnosticsController.InfoRequest("4000318883", base64), admin);

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
    void refusesNonAdminBeforeCallingDps() {
        Authentication cashier = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_COUNTER"))).when(cashier).getAuthorities();

        assertThrows(ResponseStatusException.class, () -> controller.info(
                new PrroDiagnosticsController.InfoRequest("4000318883", "c2lnbg=="), cashier));
        verifyNoInteractions(client);
    }
}
