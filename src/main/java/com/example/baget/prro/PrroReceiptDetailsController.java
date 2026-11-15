package com.example.baget.prro;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/prro/receipt-details")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroReceiptDetailsController {
    private final JdbcTemplate db;
    private final PrroAccessService access;

    public PrroReceiptDetailsController(JdbcTemplate db, PrroAccessService access) {
        this.db = Objects.requireNonNull(db);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping("/{branchNo}")
    public ReceiptDetails get(@PathVariable long branchNo, Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);

        List<ReceiptDetails> rows = db.query("""
                SELECT b.BranchName, b.address AS branch_address,
                       c.full_name, c.edrpou, c.ipn
                  FROM branches b
                  JOIN company_details c ON c.id = 1
                 WHERE b.BranchNo = ?
                """, (rs, row) -> {
            String ipn = rs.getString("ipn");
            boolean vatPayer = ipn != null && !ipn.isBlank();
            String taxId = vatPayer ? ipn : rs.getString("edrpou");
            return new ReceiptDetails(
                    rs.getString("full_name"),
                    vatPayer ? "ПН" : "ІД",
                    taxId,
                    rs.getString("BranchName"),
                    rs.getString("branch_address"));
        }, branchNo);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Не знайдено реквізити підприємства або активну господарську одиницю.");
        }
        ReceiptDetails details = rows.get(0);
        if (details.companyName() == null || details.companyName().isBlank()
                || details.taxId() == null || details.taxId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не заповнені реквізити суб'єкта господарювання. Адміністратор має відкрити Налаштування → Реквізити підприємства та вказати найменування і ЄДРПОУ / РНОКПП або ІПН платника ПДВ.");
        }
        if (details.branchAddress() == null || details.branchAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не вказано адресу цієї господарської одиниці. Адміністратор має відкрити Налаштування → Керування філіями та заповнити адресу філії.");
        }
        return details;
    }

    @PostMapping("/{branchNo}/qr")
    public QrCodeResponse qr(@PathVariable long branchNo,
                             @Valid @RequestBody QrCodeRequest request,
                             Authentication authentication) {
        var user = access.requireOperationalUser(authentication);
        access.requireBranchAccess(user, branchNo);

        var url = UriComponentsBuilder.fromHttpUrl("https://cabinet.tax.gov.ua/cashregs/check");
        if (request.mac() != null && !request.mac().isBlank()) {
            url.queryParam("mac", request.mac());
        }
        String verificationUrl = url
                .queryParam("date", request.dateTime().substring(0, 8))
                .queryParam("time", request.dateTime().substring(8, 14))
                .queryParam("id", request.receiptNumber())
                .queryParam("sm", new BigDecimal(request.totalMinor()).movePointLeft(2).setScale(2).toPlainString())
                .queryParam("fn", request.fiscalNumber())
                .build()
                .encode()
                .toUriString();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            var matrix = new QRCodeWriter().encode(verificationUrl, BarcodeFormat.QR_CODE, 320, 320, hints);
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            String imageDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            return new QrCodeResponse(verificationUrl, imageDataUrl);
        } catch (WriterException | IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не вдалося сформувати QR-код фіскального чека.", ex);
        }
    }

    public record QrCodeRequest(
            @NotBlank @Pattern(regexp = "\\d{1,32}") String fiscalNumber,
            @NotBlank @Pattern(regexp = "\\d{1,32}") String receiptNumber,
            @NotBlank @Pattern(regexp = "\\d{14}") String dateTime,
            @NotBlank @Pattern(regexp = "\\d{1,12}") String totalMinor,
            @Pattern(regexp = "(?i)[0-9a-f]{1,128}") String mac) {}

    public record QrCodeResponse(String verificationUrl, String imageDataUrl) {}

    public record ReceiptDetails(
            String companyName,
            String taxIdLabel,
            String taxId,
            String branchName,
            String branchAddress) {}
}