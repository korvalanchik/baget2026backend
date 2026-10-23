package com.example.baget.prro.client;

import com.example.baget.prro.config.PrroProperties;
import com.programika.rro.ws.chk.Check;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.ChkIncomeServiceGrpc;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DpsPrroGrpcClientTest {

    @Test
    void appliesConfiguredDeadlineToSubmissionCall() {
        ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub stub =
                mock(ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub.class);
        PrroProperties properties = new PrroProperties();
        Check check = Check.newBuilder().setRroFn("test-rro").setLocalNumber(1).build();
        CheckResponse response = CheckResponse.newBuilder()
                .setStatus(CheckResponse.Status.OK).setId("fiscal-1").build();

        when(stub.withDeadlineAfter(15_000L, TimeUnit.MILLISECONDS)).thenReturn(stub);
        when(stub.sendChkV2(check)).thenReturn(response);

        DpsPrroGrpcClient client = new DpsPrroGrpcClient(stub, properties);

        assertSame(response, client.sendCheckV2(check));
        verify(stub).withDeadlineAfter(15_000L, TimeUnit.MILLISECONDS);
        verify(stub).sendChkV2(check);
    }

    @Test
    void rejectsNonPositiveDeadline() {
        ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub stub =
                mock(ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub.class);
        PrroProperties properties = new PrroProperties();
        properties.setRpcDeadlineMillis(0);

        assertThrows(IllegalArgumentException.class, () -> new DpsPrroGrpcClient(stub, properties));
    }
}
