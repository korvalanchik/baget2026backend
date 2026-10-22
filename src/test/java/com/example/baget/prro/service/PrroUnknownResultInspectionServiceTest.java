package com.example.baget.prro.service;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrroUnknownResultInspectionServiceTest {

    private PrroOperationService operations;
    private DpsPrroGrpcClient client;
    private PrroUnknownResultInspectionService service;

    @BeforeEach
    void setUp() {
        operations = mock(PrroOperationService.class);
        client = mock(DpsPrroGrpcClient.class);
        service = new PrroUnknownResultInspectionService(operations, client);
    }

    @Test
    void inspectsStatusAndLastCheckWithoutMutatingOperation() {
        var operation = operation(PrroOperationService.Status.UNKNOWN_RESULT);
        var request = request();
        var status = StatusResponse.newBuilder().setStatus(StatusResponse.Status.OK)
                .setOpenShift(true).setOnline(true).build();
        var last = CheckResponse.newBuilder().setStatus(CheckResponse.Status.OK)
                .setId("some-server-id").build();
        when(operations.get(9L)).thenReturn(operation);
        when(client.getRroStatus(request)).thenReturn(status);
        when(client.getLastCheck(request)).thenReturn(last);

        var result = service.inspect(9L, request);

        assertSame(operation, result.operation());
        assertSame(status, result.rroStatus());
        assertSame(last, result.lastCheck());
        verify(client).getRroStatus(request);
        verify(client).getLastCheck(request);
        verifyNoMoreInteractions(operations, client);
    }

    @Test
    void refusesInspectionWithoutRroSignatureBeforeCallingServices() {
        assertThrows(IllegalArgumentException.class,
                () -> service.inspect(9L, CheckRequest.getDefaultInstance()));
        verifyNoInteractions(operations, client);
    }

    @Test
    void refusesToInspectAnOperationWhoseResultIsKnown() {
        when(operations.get(9L)).thenReturn(operation(PrroOperationService.Status.SIGNED));

        assertThrows(IllegalStateException.class, () -> service.inspect(9L, request()));
        verifyNoInteractions(client);
    }

    private static CheckRequest request() {
        return CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFromUtf8("signed-rro-fn"))
                .build();
    }

    private static PrroOperationService.Operation operation(PrroOperationService.Status status) {
        return new PrroOperationService.Operation(9L, 3L, "shift-1", "idem-1", "1".repeat(64),
                "PAYMENT", "42", "CHK", 12, status, "2".repeat(64), null,
                null, null, "cashier");
    }
}
