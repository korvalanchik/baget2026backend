package com.example.baget.finance;

import com.example.baget.my_company.CompanyDetails;
import com.example.baget.my_company.CompanyDetailsService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class CustomerPaymentReceiptPdf {
    private final CompanyDetailsService companyDetailsService;

    public CustomerPaymentReceiptPdf(CompanyDetailsService companyDetailsService) {
        this.companyDetailsService = companyDetailsService;
    }

    public record TransactionLine(String description, BigDecimal amount) {}
    public record CashLine(String accountName, String accountKind, BigDecimal amount) {}
    public record ReceiptData(
            String receiptNumber,
            String createdAt,
            String customerName,
            String customerPhone,
            String branchName,
            String cashier,
            Long invoiceNo,
            BigDecimal cashReceived,
            BigDecimal advanceAllocated,
            String note,
            List<TransactionLine> transactions,
            List<CashLine> cashLines
    ) {}

    public byte[] render(ReceiptData receipt) throws Exception {
        CompanyDetails supplier = companyDetailsService.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 28, 28, 30, 30);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont base = BaseFont.createFont(
                Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font normal = new Font(base, 9);
        Font small = new Font(base, 7);
        Font bold = new Font(base, 9, Font.BOLD);
        Font title = new Font(base, 13, Font.BOLD);

        add(document, text(supplier.getFullName()), title);
        if (supplier.getEdrpou() != null && !supplier.getEdrpou().isBlank()) add(document, "ЄДРПОУ: " + supplier.getEdrpou(), normal);
        if (supplier.getIpn() != null && !supplier.getIpn().isBlank()) add(document, "ІПН: " + supplier.getIpn(), normal);
        if (supplier.getPhone() != null && !supplier.getPhone().isBlank()) add(document, "Телефон: " + supplier.getPhone(), normal);
        if (supplier.getBankAccount() != null && !supplier.getBankAccount().isBlank()) add(document, supplier.getBankAccount(), normal);
        if (supplier.getAddress() != null && !supplier.getAddress().isBlank()) add(document, supplier.getAddress(), normal);
        if (supplier.getComment() != null && !supplier.getComment().isBlank()) add(document, supplier.getComment(), small);

        Paragraph heading = new Paragraph("КВИТАНЦІЯ ПРО РОЗРАХУНОК З КЛІЄНТОМ", title);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(4);
        document.add(heading);

        Paragraph number = new Paragraph("№ " + text(receipt.receiptNumber()) + " · " + text(receipt.createdAt()), bold);
        number.setAlignment(Element.ALIGN_CENTER);
        number.setSpacingAfter(12);
        document.add(number);

        add(document, "Філія: " + text(receipt.branchName()), normal);
        add(document, "Клієнт: " + text(receipt.customerName()), normal);
        if (receipt.customerPhone() != null && !receipt.customerPhone().isBlank()) add(document, "Телефон: " + receipt.customerPhone(), normal);
        add(document, "Касир / користувач: " + text(receipt.cashier()), normal);
        if (receipt.invoiceNo() != null) add(document, "Рахунок: № " + receipt.invoiceNo(), normal);

        PdfPTable operations = new PdfPTable(new float[]{5.5f, 1.8f});
        operations.setWidthPercentage(100);
        operations.setSpacingBefore(10);
        operations.setHeaderRows(1);
        addHeader(operations, "Операція", bold);
        addHeader(operations, "Сума, грн", bold);
        for (TransactionLine line : receipt.transactions()) {
            addCell(operations, line.description(), normal, Element.ALIGN_LEFT);
            addCell(operations, money(line.amount()), normal, Element.ALIGN_RIGHT);
        }
        document.add(operations);

        BigDecimal received = zero(receipt.cashReceived());
        BigDecimal advance = zero(receipt.advanceAllocated());
        BigDecimal total = received.add(advance);
        Paragraph summary = new Paragraph("Нових коштів отримано: " + money(received) + " грн", bold);
        summary.setSpacingBefore(10);
        document.add(summary);
        document.add(new Paragraph("Зараховано з раніше внесеного авансу: " + money(advance) + " грн", normal));
        Paragraph totalLine = new Paragraph("РАЗОМ за розрахунком: " + money(total) + " грн", title);
        totalLine.setAlignment(Element.ALIGN_RIGHT);
        totalLine.setSpacingBefore(4);
        totalLine.setSpacingAfter(8);
        document.add(totalLine);

        if (!receipt.cashLines().isEmpty()) {
            add(document, "Куди надійшли нові кошти:", bold);
            PdfPTable accounts = new PdfPTable(new float[]{4.5f, 1.8f, 1.8f});
            accounts.setWidthPercentage(100);
            addHeader(accounts, "Каса / рахунок", bold);
            addHeader(accounts, "Тип", bold);
            addHeader(accounts, "Сума, грн", bold);
            for (CashLine line : receipt.cashLines()) {
                addCell(accounts, text(line.accountName()), normal, Element.ALIGN_LEFT);
                addCell(accounts, accountKind(line.accountKind()), normal, Element.ALIGN_LEFT);
                addCell(accounts, money(line.amount()), normal, Element.ALIGN_RIGHT);
            }
            document.add(accounts);
        }

        if (receipt.note() != null && !receipt.note().isBlank()) {
            Paragraph note = new Paragraph("Примітка: " + receipt.note(), normal);
            note.setSpacingBefore(8);
            document.add(note);
        }

        Paragraph nonFiscal = new Paragraph(
                "Цей документ підтверджує запис у внутрішньому обліку. Він НЕ є фіскальним чеком РРО/ПРРО.",
                bold);
        nonFiscal.setSpacingBefore(14);
        document.add(nonFiscal);
        Paragraph footer = new Paragraph(
                "Для видачі фіскального чека потрібне окреме підключення РРО/ПРРО. " +
                        "Зберігайте цю квитанцію разом із платіжними документами.",
                small);
        footer.setSpacingBefore(3);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private void add(Document document, String value, Font font) throws DocumentException {
        document.add(new Paragraph(value, font));
    }

    private void addHeader(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBackgroundColor(new java.awt.Color(235, 240, 243));
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String accountKind(String value) {
        if (value == null) return "—";
        return switch (value) {
            case "BRANCH_CASH" -> "Каса філії";
            case "CENTRAL_CASH" -> "Центральна каса";
            case "BANK" -> "Банк";
            case "TRANSIT" -> "Транзит";
            default -> value;
        };
    }

    private String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("uk", "UA"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(zero(amount));
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
