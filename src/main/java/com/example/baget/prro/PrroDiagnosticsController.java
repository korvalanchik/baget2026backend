package com.example.baget.prro;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.google.protobuf.ByteString;
import com.programika.rro.ws.chk.CheckRequest;
import com.programika.rro.ws.chk.RroInfoResponse;
import com.programika.rro.ws.chk.StatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Read-only PRRO diagnostic endpoint used by the cashier UI to verify the
 * IIT widget signature envelope against the DPS test/production endpoint.
 * It never signs data and never submits a fiscal receipt.
 */
@RestController
@RequestMapping("/api/prro/diagnostics")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroDiagnosticsController {
    private final DpsPrroGrpcClient client;

    public PrroDiagnosticsController(DpsPrroGrpcClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @PostMapping("/info")
    public DiagnosticResponse info(@Valid @RequestBody InfoRequest request,
                                   Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Перевірка ПРРО доступна лише адміністратору.");
        }

        final byte[] signature;
        try {
            signature = Base64.getDecoder().decode(request.signatureBase64());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Підпис має бути коректним рядком Base64.", ex);
        }
        if (signature.length == 0 || signature.length > 64 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некоректний розмір підпису ПРРО.");
        }

        CheckRequest signedRequest = CheckRequest.newBuilder()
                .setRroFnSign(ByteString.copyFrom(signature))
                .build();

        RroInfoResponse info = client.getRroInfo(signedRequest);
        StatusResponse status = client.getRroStatus(signedRequest);
        List<OperatorView> operators = info.getOperatorsList().stream()
                .map(operator -> new OperatorView(operator.getSerial(), operator.getStatus(),
                        operator.getSenior(), operator.getIsname()))
                .toList();

        return new DiagnosticResponse(request.fiscalNumber(), info.getStatus().name(),
                info.getStatusRro(), info.getOpenShift(), info.getOnline(),
                info.getLastSigner(), info.getName(), info.getNameTo(), info.getAddr(),
                info.getSingleTax(), info.getOfflineAllowed(), info.getAddNum(),
                info.getPn(), info.getTins(), info.getLnum(), info.getNamePay(), operators,
                status.getStatus().name(), status.getOpenShift(), status.getOnline(),
                status.getLastSigner(), status.getErrorMessage());
    }

    public record InfoRequest(
            @NotBlank @Pattern(regexp = "[0-9]{1,32}", message = "Фіскальний номер має містити лише цифри.")
            String fiscalNumber,
            @NotBlank @Size(max = 100_000) String signatureBase64) {}

    public record OperatorView(String serial, int status, boolean senior, String name) {}

    public record DiagnosticResponse(
            String fiscalNumber,
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
}
