package com.example.baget.prro.service;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.Check;
import com.programika.rro.ws.chk.CheckResponse;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrroSubmissionServiceTest {

    private PrroOperationService operations;
    private DpsPrroGrpcClient client;
    private PrroSubmissionService service;

    @BeforeEach
    void setUp() {
        operations = mock(PrroOperationService.class);
        client = mock(DpsPrroGrpcClient.class);
        service = new PrroSubmissionService(operations, client);
    }

    @Test
    void fiscalizesSuccessfulResponse() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        when(client.sendCheckV2(any(Check.class))).thenReturn(CheckResponse.newBuilder()
                .setId("fiscal-123").setStatus(CheckResponse.Status.OK).build());
        when(operations.transition(eq(7L), eq(PrroOperationService.Status.SENDING),
                eq(PrroOperationService.Status.FISCALIZED), eq(hash), eq("fiscal-123"),
                eq(1), any())).thenReturn(operation(PrroOperationService.Status.FISCALIZED, hash));

        var result = service.submitSignedDocument(7L, check(signedXml));

        assertEquals(PrroOperationService.Status.FISCALIZED, result.status());
        verify(operations).transition(eq(7L), eq(PrroOperationService.Status.SIGNED),
                eq(PrroOperationService.Status.SENDING), eq(hash), isNull(), isNull(), isNull());
    }

    @Test
    void recordsRejectedResponse() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        when(client.sendCheckV2(any(Check.class))).thenReturn(CheckResponse.newBuilder()
                .setStatus(CheckResponse.Status.ERROR_XML).setErrorMessage("Invalid XML").build());
        when(operations.transition(eq(7L), eq(PrroOperationService.Status.SENDING),
                eq(PrroOperationService.Status.REJECTED), eq(hash), isNull(), eq(-7),
                eq("Invalid XML"))).thenReturn(operation(PrroOperationService.Status.REJECTED, hash));

        var result = service.submitSignedDocument(7L, check(signedXml));

        assertEquals(PrroOperationService.Status.REJECTED, result.status());
    }

    @Test
    void marksTransportFailureAsUnknownAndDoesNotRetry() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        when(client.sendCheckV2(any(Check.class))).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        when(operations.transition(eq(7L), eq(PrroOperationService.Status.SENDING),
                eq(PrroOperationService.Status.UNKNOWN_RESULT), eq(hash), isNull(), isNull(),
                contains("UNAVAILABLE"))).thenReturn(operation(PrroOperationService.Status.UNKNOWN_RESULT, hash));

        var error = assertThrows(PrroSubmissionService.SubmissionOutcomeUnknownException.class,
                () -> service.submitSignedDocument(7L, check(signedXml)));

        assertEquals(7L, error.getOperationId());
        verify(client, times(1)).sendCheckV2(any(Check.class));
    }

    @Test
    void rejectsMismatchedSignedDocumentBeforeSending() {
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, "0".repeat(64)));

        assertThrows(IllegalArgumentException.class,
                () -> service.submitSignedDocument(7L, check("<different/>".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8))));
        verifyNoInteractions(client);
    }

    private static Check check(byte[] signedXml) {
        return Check.newBuilder().setRroFn("TEST-RRO").setDateTime(1L)
                .setLocalNumber(12).setCheckType(Check.Type.CHK)
                .setCheckSign(ByteString.copyFrom(signedXml)).build();
    }

    private static PrroOperationService.Operation operation(PrroOperationService.Status status, String hash) {
        return new PrroOperationService.Operation(7L, 3L, "shift-1", "idem-1", "1".repeat(64),
                "PAYMENT", "42", "CHK", 12, status, hash, null, null, null, "cashier");
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
