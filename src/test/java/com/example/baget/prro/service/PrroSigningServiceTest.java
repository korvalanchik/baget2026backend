package com.example.baget.prro.service;

import com.example.baget.prro.signing.PrroDocumentSigner;
import com.example.baget.prro.signing.PrroDocumentSigner.SignedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrroSigningServiceTest {

    private PrroOperationService operations;
    private PrroDocumentSigner signer;
    private PrroSigningService service;

    @BeforeEach
    void setUp() {
        operations = mock(PrroOperationService.class);
        signer = mock(PrroDocumentSigner.class);
        service = new PrroSigningService(operations, signer);
    }

    @Test
    void signsPreparedOperationAndStoresHashOfExactReturnedPayload() {
        byte[] signingInput = "<DAT/>".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = "<signed/>".getBytes(StandardCharsets.UTF_8);
        String expectedHash = sha256(signedBytes);
        PrroOperationService.Operation prepared = operation(
                PrroOperationService.Status.PREPARED, "operator-key-1", null);
        PrroOperationService.Operation signed = operation(
                PrroOperationService.Status.SIGNED, "operator-key-1", expectedHash);
        when(operations.get(7L)).thenReturn(prepared);
        when(signer.sign(eq(prepared), any(byte[].class)))
                .thenReturn(new SignedPayload(signedBytes, "operator-key-1"));
        when(operations.transition(7L, PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.SIGNED, expectedHash, null, null, null))
                .thenReturn(signed);

        PrroSigningService.SigningResult result = service.signPreparedDocument(7L, signingInput);

        assertEquals(PrroOperationService.Status.SIGNED, result.operation().status());
        assertEquals("operator-key-1", result.signerKeyId());
        assertEquals(expectedHash, result.signedPayloadSha256());
        assertArrayEquals(signedBytes, result.signedPayload());
        verify(operations).transition(7L, PrroOperationService.Status.PREPARED,
                PrroOperationService.Status.SIGNED, expectedHash, null, null, null);
    }

    @Test
    void refusesToRecordPayloadIfSignerAdapterUsedDifferentKey() {
        byte[] signingInput = "<DAT/>".getBytes(StandardCharsets.UTF_8);
        PrroOperationService.Operation prepared = operation(
                PrroOperationService.Status.PREPARED, "operator-key-1", null);
        when(operations.get(7L)).thenReturn(prepared);
        when(signer.sign(eq(prepared), any(byte[].class)))
                .thenReturn(new SignedPayload("<signed/>".getBytes(StandardCharsets.UTF_8),
                        "operator-key-2"));

        assertThrows(PrroSigningService.SignerIdentityMismatchException.class,
                () -> service.signPreparedDocument(7L, signingInput));

        verify(operations, never()).transition(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsOperationThatIsNotPreparedBeforeCallingSigner() {
        when(operations.get(7L)).thenReturn(operation(
                PrroOperationService.Status.SIGNED, "operator-key-1", "a".repeat(64)));

        assertThrows(IllegalStateException.class,
                () -> service.signPreparedDocument(7L, "<DAT/>".getBytes(StandardCharsets.UTF_8)));

        verifyNoInteractions(signer);
    }

    @Test
    void rejectsPreparedOperationWithoutBoundSignerBeforeCallingSigner() {
        when(operations.get(7L)).thenReturn(operation(
                PrroOperationService.Status.PREPARED, null, null));

        assertThrows(IllegalStateException.class,
                () -> service.signPreparedDocument(7L, "<DAT/>".getBytes(StandardCharsets.UTF_8)));

        verifyNoInteractions(signer);
    }

    @Test
    void rejectsEmptySigningInputBeforeDatabaseLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.signPreparedDocument(7L, new byte[0]));

        verifyNoInteractions(operations, signer);
    }

    @Test
    void signedPayloadDefensivelyCopiesBytes() {
        byte[] original = "<signed/>".getBytes(StandardCharsets.UTF_8);
        SignedPayload payload = new SignedPayload(original, " operator-key-1 ");
        original[0] = 'X';
        byte[] returned = payload.bytes();
        returned[0] = 'Y';

        assertArrayEquals("<signed/>".getBytes(StandardCharsets.UTF_8), payload.bytes());
        assertEquals("operator-key-1", payload.signerKeyId());
    }

    private static PrroOperationService.Operation operation(
            PrroOperationService.Status status, String signerKeyId, String signedHash) {
        return new PrroOperationService.Operation(
                7L, 3L, "shift-1", "idempotency-1", "a".repeat(64),
                "PAYMENT", "42", "CHK", 12, signerKeyId, status,
                signedHash, null, null, null, "cashier");
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
