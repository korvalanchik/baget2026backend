package com.example.baget.prro.client;

import com.programika.rro.ws.chk.Check;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckRequestId;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.ChkIncomeServiceGrpc;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

import java.util.Objects;

/**
 * Thin transport adapter for the official DPS gRPC API.
 *
 * This class intentionally does not construct XML, calculate the previous-document
 * hash, sign documents, or decide whether an ambiguous request should be retried.
 * Those responsibilities belong to higher-level services and must be implemented
 * against the complete current DPS protocol.
 */
@Component
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class DpsPrroGrpcClient {

    private final ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub stub;
    private final long rpcDeadlineMillis;

    public DpsPrroGrpcClient(ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub stub,
                             com.example.baget.prro.config.PrroProperties properties) {
        this.stub = Objects.requireNonNull(stub, "stub");
        Objects.requireNonNull(properties, "properties");
        if (properties.getRpcDeadlineMillis() <= 0) {
            throw new IllegalArgumentException("PRRO RPC deadline must be positive");
        }
        this.rpcDeadlineMillis = properties.getRpcDeadlineMillis();
    }

    private ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub withDeadline() {
        return stub.withDeadlineAfter(rpcDeadlineMillis, TimeUnit.MILLISECONDS);
    }

    public CheckResponse sendCheckV2(Check check) {
        return withDeadline().sendChkV2(Objects.requireNonNull(check, "check"));
    }

    public CheckResponse ping(Check check) {
        return withDeadline().ping(Objects.requireNonNull(check, "check"));
    }

    public CheckResponse getLastCheck(CheckRequest request) {
        return withDeadline().lastChk(Objects.requireNonNull(request, "request"));
    }

    public CheckResponse deleteLastCheck(CheckRequest request) {
        return withDeadline().delLastChk(Objects.requireNonNull(request, "request"));
    }

    public CheckResponse deleteLastCheckById(CheckRequestId request) {
        return withDeadline().delLastChkId(Objects.requireNonNull(request, "request"));
    }

    public StatusResponse getRroStatus(CheckRequest request) {
        return withDeadline().statusRro(Objects.requireNonNull(request, "request"));
    }

    public RroInfoResponse getRroInfo(CheckRequest request) {
        return withDeadline().infoRro(Objects.requireNonNull(request, "request"));
    }
}
