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
    private PrroFiscalDocumentArchiveService archive;
    private PrroSubmissionService service;

    @BeforeEach
    void setUp() {
        operations = mock(PrroOperationService.class);
        client = mock(DpsPrroGrpcClient.class);
        archive = mock(PrroFiscalDocumentArchiveService.class);
        service = new PrroSubmissionService(operations, client, archive);
    }

    @Test
    void fiscalizesSuccessfulResponse() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        CheckResponse response = CheckResponse.newBuilder()
                .setId("fiscal-123").setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(new byte[]{0x30, 0x01, 0x02}))
                .setIdSign(ByteString.copyFrom(new byte[]{0x30, 0x01, 0x03}))
                .build();
        when(client.sendCheckV2(any(Check.class))).thenReturn(response);
        when(archive.completeFiscalized(7L, hash, response))
                .thenReturn(operation(PrroOperationService.Status.FISCALIZED, hash));

        var result = service.submitSignedDocument(7L, check(signedXml), rroSignature());

        assertEquals(PrroOperationService.Status.FISCALIZED, result.status());
        verify(operations).transition(eq(7L), eq(PrroOperationService.Status.SIGNED),
                eq(PrroOperationService.Status.SENDING), eq(hash), isNull(), isNull(), isNull());
        verify(archive).completeFiscalized(7L, hash, CheckResponse.newBuilder()
                .setId("fiscal-123").setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(new byte[]{0x30, 0x01, 0x02}))
                .setIdSign(ByteString.copyFrom(new byte[]{0x30, 0x01, 0x03}))
                .build());
        verify(client, never()).getLastCheck(any(com.programika.rro.ws.chk.CheckRequest.class));
    }

    @Test
    void marksAcceptedDocumentUnknownWhenOriginalResponseCannotBeArchived() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        CheckResponse response = CheckResponse.newBuilder()
                .setId("fiscal-archive-failed")
                .setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(new byte[]{0x30, 0x01}))
                .build();
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash),
                operation(PrroOperationService.Status.SENDING, hash));
        when(client.sendCheckV2(any(Check.class))).thenReturn(response);
        when(archive.completeFiscalized(7L, hash, response))
                .thenThrow(new IllegalStateException("archive unavailable"));
        when(operations.transition(eq(7L), eq(PrroOperationService.Status.SENDING),
                eq(PrroOperationService.Status.UNKNOWN_RESULT), eq(hash),
                eq("fiscal-archive-failed"), eq(1), contains("could not be archived")))
                .thenReturn(operation(PrroOperationService.Status.UNKNOWN_RESULT, hash));

        var result = service.submitSignedDocument(7L, check(signedXml), rroSignature());

        assertEquals(PrroOperationService.Status.UNKNOWN_RESULT, result.status());
        verify(operations).transition(eq(7L), eq(PrroOperationService.Status.SENDING),
                eq(PrroOperationService.Status.UNKNOWN_RESULT), eq(hash),
                eq("fiscal-archive-failed"), eq(1), contains("could not be archived"));
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

        var result = service.submitSignedDocument(7L, check(signedXml), rroSignature());

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
                () -> service.submitSignedDocument(7L, check(signedXml), rroSignature()));

        assertEquals(7L, error.getOperationId());
        verify(client, times(1)).sendCheckV2(any(Check.class));
    }

    @Test
    void rejectsMismatchedSignedDocumentBeforeSending() {
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, "0".repeat(64)));

        assertThrows(IllegalArgumentException.class,
                () -> service.submitSignedDocument(7L, check("<different/>".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)), rroSignature()));
        verifyNoInteractions(client);
    }


    @Test
    void obtainsOriginalDataSignFromLastChkWhenSendResponseOmitsIt() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        CheckResponse sent = CheckResponse.newBuilder()
                .setId("dps-document-1").setStatus(CheckResponse.Status.OK).build();
        byte[] dataSign = new byte[]{0x30, (byte) 0x82, 0x01, 0x02};
        byte[] idSign = new byte[]{0x30, 0x01, 0x03};
        CheckResponse last = CheckResponse.newBuilder()
                .setId("dps-document-1").setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(dataSign))
                .setIdSign(ByteString.copyFrom(idSign)).build();
        when(client.sendCheckV2(any(Check.class))).thenReturn(sent);
        when(client.getLastCheck(any(com.programika.rro.ws.chk.CheckRequest.class))).thenReturn(last);
        when(archive.completeFiscalized(eq(7L), eq(hash), any(CheckResponse.class)))
                .thenReturn(operation(PrroOperationService.Status.FISCALIZED, hash));

        var result = service.submitSignedDocument(7L, check(signedXml), rroSignature());

        assertEquals(PrroOperationService.Status.FISCALIZED, result.status());
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(
                com.programika.rro.ws.chk.CheckRequest.class);
        verify(client).getLastCheck(requestCaptor.capture());
        assertEquals(ByteString.copyFrom("rro-signature".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)), requestCaptor.getValue().getRroFnSign());
        var responseCaptor = org.mockito.ArgumentCaptor.forClass(CheckResponse.class);
        verify(archive).completeFiscalized(eq(7L), eq(hash), responseCaptor.capture());
        assertEquals("dps-document-1", responseCaptor.getValue().getId());
        assertEquals(ByteString.copyFrom(dataSign), responseCaptor.getValue().getDataSign());
        assertEquals(ByteString.copyFrom(idSign), responseCaptor.getValue().getIdSign());
    }

    @Test
    void neverArchivesLastChkBytesWhenDpsDocumentIdDoesNotMatch() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        CheckResponse sent = CheckResponse.newBuilder()
                .setId("expected-document").setStatus(CheckResponse.Status.OK).build();
        CheckResponse unrelated = CheckResponse.newBuilder()
                .setId("another-document").setStatus(CheckResponse.Status.OK)
                .setDataSign(ByteString.copyFrom(new byte[]{0x30, 0x01})).build();
        when(client.sendCheckV2(any(Check.class))).thenReturn(sent);
        when(client.getLastCheck(any(com.programika.rro.ws.chk.CheckRequest.class))).thenReturn(unrelated);
        when(archive.completeFiscalized(7L, hash, sent))
                .thenReturn(operation(PrroOperationService.Status.FISCALIZED, hash));

        service.submitSignedDocument(7L, check(signedXml), rroSignature());

        verify(archive).completeFiscalized(7L, hash, sent);
    }

    @Test
    void keepsFiscalizedResponseWhenLastChkIsUnavailable() {
        byte[] signedXml = "<signed/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = sha256(signedXml);
        when(operations.get(7L)).thenReturn(operation(PrroOperationService.Status.SIGNED, hash));
        CheckResponse sent = CheckResponse.newBuilder()
                .setId("accepted-document").setStatus(CheckResponse.Status.OK).build();
        when(client.sendCheckV2(any(Check.class))).thenReturn(sent);
        when(client.getLastCheck(any(com.programika.rro.ws.chk.CheckRequest.class)))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException());
        when(archive.completeFiscalized(7L, hash, sent))
                .thenReturn(operation(PrroOperationService.Status.FISCALIZED, hash));

        var result = service.submitSignedDocument(7L, check(signedXml), rroSignature());

        assertEquals(PrroOperationService.Status.FISCALIZED, result.status());
        verify(archive).completeFiscalized(7L, hash, sent);
    }

    private static String rroSignature() {
        return java.util.Base64.getEncoder().encodeToString(
                "rro-signature".getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
