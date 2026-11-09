package com.example.baget.prro;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.CheckResponse;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Read-only PRRO diagnostics. These endpoints never sign data or submit/delete
 * fiscal documents.
 */
@RestController
@RequestMapping("/api/prro/diagnostics")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroDiagnosticsController {
    private final DpsPrroGrpcClient client;
    private final PrroAccessService access;

    public PrroDiagnosticsController(DpsPrroGrpcClient client, PrroAccessService access) {
        this.client = Objects.requireNonNull(client, "client");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PostMapping("/info")
    public DiagnosticResponse info(@Valid @RequestBody InfoRequest request,
                                   Authentication authentication) {
        CheckRequest signedRequest = signedRequest(request.signatureBase64(), authentication);

        RroInfoResponse info = client.getRroInfo(signedRequest);
        StatusResponse status = client.getRroStatus(signedRequest);
        List<OperatorView> operators = info.getOperatorsList().stream()
                .map(operator -> new OperatorView(operator.getSerial(), operator.getStatus(),
                        operator.getSenior(), operator.getIsname()))
                .toList();

        // DPS resolves the RRO from the signed payload. Do not echo a caller-
        // supplied fiscal number as though it were confirmed by DPS.
        return new DiagnosticResponse(info.getStatus().name(),
                info.getStatusRro(), info.getOpenShift(), info.getOnline(),
                info.getLastSigner(), info.getName(), info.getNameTo(), info.getAddr(),
                info.getSingleTax(), info.getOfflineAllowed(), info.getAddNum(),
                info.getPn(), info.getTins(), info.getLnum(), info.getNamePay(), operators,
                status.getStatus().name(), status.getOpenShift(), status.getOnline(),
                status.getLastSigner(), status.getErrorMessage());
    }

    /**
     * Reads only the current last fiscal document from DPS. No fiscal operation
     * is submitted or deleted. Binary signatures are represented by length and
     * SHA-256 only; their contents are never returned or logged.
     */
    @PostMapping("/last-check")
    public LastCheckDiagnosticResponse lastCheck(@Valid @RequestBody InfoRequest request,
                                                   Authentication authentication) {
        CheckRequest signedRequest = signedRequest(request.signatureBase64(), authentication);
        CheckResponse response = client.getLastCheck(signedRequest);

        byte[] dataSign = response.getDataSign().toByteArray();
        byte[] idSign = response.getIdSign().toByteArray();
        return new LastCheckDiagnosticResponse(
                response.getId(),
                response.getStatus().name(),
                response.getStatusValue(),
                response.getErrorMessage(),
                dataSign.length,
                sha256OrNull(dataSign),
                idSign.length,
                sha256OrNull(idSign));
    }

    private CheckRequest signedRequest(String signatureBase64, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        if (!access.isGlobalOperator(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Діагностика ПРРО доступна адміністратору та старшому касиру.");
        }

        final byte[] signature;
        try {
            signature = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис має бути коректним рядком Base64.", ex);
        }
        if (signature.length == 0 || signature.length > 64 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некоректний розмір підпису ПРРО.");
        }

        return CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(signature))
                .build();
    }

    private static String sha256OrNull(byte[] bytes) {
        if (bytes.length == 0) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record InfoRequest(@NotBlank @Size(max = 100_000) String signatureBase64) {}

    public record OperatorView(String serial, int status, boolean senior, String name) {}

    public record DiagnosticResponse(
            String infoStatus,
            int statusRro,
            boolean openShift,
            boolean online,
            String lastSigner,
            String name,
            String nameTo,
            String address,
            boolean singleTax,
            boolean offlineAllowed,
            int additionalNumber,
            String taxNumber,
            String tins,
            int localNumber,
            String paymentName,
            List<OperatorView> operators,
            String statusStatus,
            boolean statusOpenShift,
            boolean statusOnline,
            String statusLastSigner,
            String statusErrorMessage) {}

    public record LastCheckDiagnosticResponse(
            String dpsDocumentId,
            String status,
            int statusCode,
            String errorMessage,
            int dataSignLength,
            String dataSignSha256,
            int idSignLength,
            String idSignSha256) {}
}
