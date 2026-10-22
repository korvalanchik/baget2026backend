package com.example.baget.prro.service;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.StatusResponse;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Read-only inspection for ambiguous submissions. It never decides that a
 * document was fiscalized based on the last-check response alone and never
 * retries or mutates an operation automatically.
 */
@Service
public class PrroUnknownResultInspectionService {

    private final PrroOperationService operations;
    private final DpsPrroGrpcClient client;

    public PrroUnknownResultInspectionService(PrroOperationService operations,
                                              DpsPrroGrpcClient client) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.client = Objects.requireNonNull(client, "client");
    }

    public Inspection inspect(long operationId, CheckRequest signedRroRequest) {
        Objects.requireNonNull(signedRroRequest, "signedRroRequest");
        if (signedRroRequest.getRroFnSign().isEmpty()) {
            throw new IllegalArgumentException("RRO request signature must not be empty");
        }

        PrroOperationService.Operation operation = operations.get(operationId);
        if (operation.status() != PrroOperationService.Status.UNKNOWN_RESULT) {
            throw new IllegalStateException("Only UNKNOWN_RESULT operations can be inspected");
        }

        // Keep these calls read-only. A last-check response is not enough to
        // prove that this particular local operation was accepted.
        StatusResponse status = client.getRroStatus(signedRroRequest);
        CheckResponse lastCheck = client.getLastCheck(signedRroRequest);
        return new Inspection(operation, status, lastCheck);
    }

    public record Inspection(PrroOperationService.Operation operation,
                             StatusResponse rroStatus,
                             CheckResponse lastCheck) {}
}
