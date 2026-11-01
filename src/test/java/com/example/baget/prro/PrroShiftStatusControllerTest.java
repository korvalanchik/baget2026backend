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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrroShiftStatusControllerTest {
    private PrroAccessService access;
    private JdbcTemplate db;
    private DpsPrroGrpcClient client;
    private PrroShiftStatusController controller;
    private Authentication authentication;
    private User user;

    @BeforeEach
    void setUp() {
        access = mock(PrroAccessService.class);
        db = mock(JdbcTemplate.class);
        client = mock(DpsPrroGrpcClient.class);
        controller = new PrroShiftStatusController(access, db, client);
        authentication = mock(Authentication.class);
        user = mock(User.class);
        when(access.requireOperationalUser(authentication)).thenReturn(user);
    }

    @Test
    void acceptsPostJsonRequestAndRejectsGetForShiftStatus() throws Exception {
        stubDevice(null, null, 1);
        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(
                RroInfoResponse.newBuilder().setStatus(RroInfoResponse.Status.OK)
                        .setStatusRro(1).setOpenShift(false).setOnline(true).build());
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                        .setOpenShift(false).setOnline(true).build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String body = """
                {"branchNo":7,"fiscalNumber":"4000318883","signatureBase64":"AQID"}
                """;

        mvc.perform(post("/api/prro/shift/status")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mvc.perform(get("/api/prro/shift/status"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void lastCheckReturnsStatusAndDigestOfPreviousCheckDataSign() throws Exception {
        stubDevice(null, null, 1);
        byte[] previousXml = "<?xml version=\"1.0\"?><RQ V=\"1\"><DAT/></RQ>"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(client.getLastCheck(any(CheckRequest.class))).thenReturn(
                CheckResponse.newBuilder()
                        .setStatus(CheckResponse.Status.OK)
                        .setId("dps-check-123")
                        .setDataSign(ByteString.copyFrom(previousXml))
                        .build());

        var response = controller.lastCheck(new PrroShiftStatusController.ShiftStatusRequest(
                7L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})),
                authentication);

        assertEquals("OK", response.responseStatus());
        assertEquals("dps-check-123", response.fiscalDocumentId());
        assertEquals(previousXml.length, response.dataSignLength());
        assertTrue(response.dataSignStartsWithXml());
        assertEquals(64, response.dataSignSha256().length());
        verify(access).requireBranchAccess(user, 7L);
        verify(client).getLastCheck(any(CheckRequest.class));
    }

    @Test
    void exposesLastCheckInspectionThroughPostEndpoint() throws Exception {
        stubDevice(null, null, 1);
        when(client.getLastCheck(any(CheckRequest.class))).thenReturn(
                CheckResponse.newBuilder().setStatus(CheckResponse.Status.OK)
                        .setId("dps-check-123")
                        .setDataSign(ByteString.copyFromUtf8("<?xml version=\"1.0\"?><RQ/>"))
                        .build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/prro/shift/last-check")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchNo":7,"fiscalNumber":"4000318883","signatureBase64":"AQID"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void returnsOpenStateOnlyWhenDpsAndLocalDeviceStateAgree() throws Exception {
        stubDevice("shift-1", "operator-serial", 1);
        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(
                RroInfoResponse.newBuilder().setStatus(RroInfoResponse.Status.OK)
                        .setStatusRro(1).setOpenShift(true).setOnline(true).build());
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                        .setOpenShift(true).setOnline(true).setLastSigner("operator-serial").build());

        var response = controller.status(new PrroShiftStatusController.ShiftStatusRequest(7L, "4000318883",
                Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})), authentication);

        assertEquals("OPEN", response.state());
        assertTrue(response.dpsOpenShift());
        assertTrue(response.localShiftPrepared());
        assertTrue(response.localSignerBound());
        assertEquals(9L, response.deviceId());
        verify(access).branch(user, 7L);
        verify(client).getRroInfo(any(CheckRequest.class));
        verify(client).getRroStatus(any(CheckRequest.class));
    }

    @Test
    void refusesBranchAccessBeforeReadingDeviceOrCallingDps() {
        doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN))
                .when(access).requireBranchAccess(user, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.status(
                new PrroShiftStatusController.ShiftStatusRequest(7L, "4000318883", Base64.getEncoder().encodeToString(new byte[]{1})), authentication));

        verifyNoInteractions(db, client);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reportsMissingPrroForBranch() {
        doReturn(List.of()).when(db).query(anyString(), any(RowMapper.class), eq(7L));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.status(new PrroShiftStatusController.ShiftStatusRequest(7L, "4000318883",
                        Base64.getEncoder().encodeToString(new byte[]{1})), authentication));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(client);
    }

    @Test
    void flagsMismatchInsteadOfAssumingShiftIsOpen() throws Exception {
        stubDevice("shift-pending", "operator-serial", 1);
        when(client.getRroInfo(any(CheckRequest.class))).thenReturn(
                RroInfoResponse.newBuilder().setStatus(RroInfoResponse.Status.OK)
                        .setStatusRro(1).setOpenShift(false).setOnline(true).build());
        when(client.getRroStatus(any(CheckRequest.class))).thenReturn(
                StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                        .setOpenShift(false).setOnline(true).build());

        var response = controller.status(new PrroShiftStatusController.ShiftStatusRequest(7L, "4000318883",
                Base64.getEncoder().encodeToString(new byte[]{1})), authentication);

        assertEquals("RECONCILIATION_REQUIRED", response.state());
        assertFalse(response.dpsOpenShift());
        assertTrue(response.localShiftPrepared());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubDevice(String activeShiftKey, String signerKeyId, int nextLocalNumber)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(9L);
        when(rs.getString("rro_fn")).thenReturn("4000318883");
        when(rs.getString("display_name")).thenReturn("Каса тест");
        when(rs.getString("environment")).thenReturn("TEST");
        when(rs.getString("active_shift_key")).thenReturn(activeShiftKey);
        when(rs.getString("active_signer_key_id")).thenReturn(signerKeyId);
        when(rs.getInt("next_local_number")).thenReturn(nextLocalNumber);
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(db).query(anyString(), any(RowMapper.class), eq(7L));
    }
}
