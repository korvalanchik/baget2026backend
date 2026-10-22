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

    public DpsPrroGrpcClient(ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub stub) {
        this.stub = Objects.requireNonNull(stub, "stub");
    }

    public CheckResponse sendCheckV2(Check check) {
        return stub.sendChkV2(Objects.requireNonNull(check, "check"));
    }

    public CheckResponse ping(Check check) {
        return stub.ping(Objects.requireNonNull(check, "check"));
    }

    public CheckResponse getLastCheck(CheckRequest request) {
        return stub.lastChk(Objects.requireNonNull(request, "request"));
    }

    public CheckResponse deleteLastCheck(CheckRequest request) {
        return stub.delLastChk(Objects.requireNonNull(request, "request"));
    }

    public CheckResponse deleteLastCheckById(CheckRequestId request) {
        return stub.delLastChkId(Objects.requireNonNull(request, "request"));
    }

    public StatusResponse getRroStatus(CheckRequest request) {
        return stub.statusRro(Objects.requireNonNull(request, "request"));
    }

    public RroInfoResponse getRroInfo(CheckRequest request) {
        return stub.infoRro(Objects.requireNonNull(request, "request"));
    }
}
