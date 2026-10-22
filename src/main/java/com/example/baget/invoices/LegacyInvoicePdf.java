package com.example.baget.invoices;

import com.example.baget.billing.BillingMath;
import com.example.baget.items.ItemViewDTO;
import com.example.baget.my_company.CompanyDetails;
import com.example.baget.my_company.CompanyDetailsService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Creates a printable document from the saved invoice and invoice_orders snapshot.
 * It does not reconstruct the invoice from the current RahFacNo values on orders.
 */
@Service
public class LegacyInvoicePdf {
    private final CompanyDetailsService companyDetailsService;

    public LegacyInvoicePdf(CompanyDetailsService companyDetailsService) {
        this.companyDetailsService = companyDetailsService;
    }

    public byte[] render(InvoiceViewDTO invoice, String payerName, String note,
                         String lifecycle, BigDecimal currentDebt) throws Exception {
        CompanyDetails supplier = companyDetailsService.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 42, 42);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont base = BaseFont.createFont(
                Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font normal = new Font(base, 9);
        Font bold = new Font(base, 10, Font.BOLD);
        Font title = new Font(base, 14, Font.BOLD);
        Font small = new Font(base, 8);

        add(document, "Постачальник: " + text(supplier.getFullName()), bold);
        if (supplier.getEdrpou() != null) add(document, "ЄДРПОУ: " + supplier.getEdrpou(), normal);
        if (supplier.getIpn() != null) add(document, "ІПН: " + supplier.getIpn(), normal);
        if (supplier.getPhone() != null) add(document, "Телефон: " + supplier.getPhone(), normal);
        if (supplier.getBankAccount() != null) add(document, "Банківські реквізити: " + supplier.getBankAccount(), normal);
        if (supplier.getAddress() != null) add(document, "Адреса: " + supplier.getAddress(), normal);
        if (supplier.getComment() != null && !supplier.getComment().isBlank()) add(document, supplier.getComment(), small);

        Paragraph heading = new Paragraph("РАХУНОК-ФАКТУРА № " + invoice.getInvoiceNo(), title);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingBefore(18);
        document.add(heading);

        OffsetDateTime createdAt = invoice.getCreatedAt();
        String date = createdAt == null ? "—" :
                createdAt.toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("uk", "UA"))) + " року";
        Paragraph dateParagraph = new Paragraph("від " + date, normal);
        dateParagraph.setAlignment(Element.ALIGN_CENTER);
        dateParagraph.setSpacingAfter(14);
        document.add(dateParagraph);

        add(document, "Одержувач: " + text(invoice.getCustomerName()), bold);
        add(document, "Платник: " + text(payerName), normal);
        add(document, "Статус: " + status(invoice.getStatus()), normal);
        if ("MERGED".equals(lifecycle)) {
            Paragraph archived = new Paragraph("Архівний рахунок: залишок боргу перенесено до іншого рахунку.", bold);
            archived.setSpacingBefore(6);
            archived.setSpacingAfter(8);
            document.add(archived);
        }

        PdfPTable table = new PdfPTable(new float[]{0.7f, 1.5f, 5.0f, 1.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingBefore(12);
        addHeader(table, "№", bold);
        addHeader(table, "№ замовлення", bold);
        addHeader(table, "Товар / послуга", bold);
        addHeader(table, "Сума, грн", bold);

        List<InvoiceOrderViewDTO> orders = invoice.getOrders() == null ? List.of() : invoice.getOrders();
        List<BigDecimal> displayedAmounts = displayedAmounts(orders, invoice.getTotalAmount());
        int index = 1;
        for (int orderIndex = 0; orderIndex < orders.size(); orderIndex++) {
            InvoiceOrderViewDTO order = orders.get(orderIndex);
            addCell(table, String.valueOf(index++), normal, Element.ALIGN_CENTER);
            addCell(table, String.valueOf(order.getOrderNo()), normal, Element.ALIGN_CENTER);
            addCell(table, describeOrder(order, supplier.getWorkTitle()), normal, Element.ALIGN_LEFT);
            addCell(table, money(displayedAmounts.get(orderIndex)), normal, Element.ALIGN_RIGHT);
        }
        if (orders.isEmpty()) {
            addCell(table, "1", normal, Element.ALIGN_CENTER);
            addCell(table, "—", normal, Element.ALIGN_CENTER);
            addCell(table, text(supplier.getWorkTitle()), normal, Element.ALIGN_LEFT);
            addCell(table, money(invoice.getTotalAmount()), normal, Element.ALIGN_RIGHT);
        }
        document.add(table);

        Paragraph total = new Paragraph("Всього за рахунком: " + money(invoice.getTotalAmount()) + " грн", bold);
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(12);
        document.add(total);
        document.add(new Paragraph("Поточний залишок до оплати на момент формування: " +
                money(currentDebt == null ? BigDecimal.ZERO : currentDebt.max(BigDecimal.ZERO)) + " грн", normal));
        if (note != null && !note.isBlank()) {
            Paragraph noteParagraph = new Paragraph("Примітка: " + note, normal);
            noteParagraph.setSpacingBefore(8);
            document.add(noteParagraph);
        }
        Paragraph signature = new Paragraph("\nВиписав(ла): " + text(supplier.getInitials()) + " __________________", normal);
        signature.setSpacingBefore(18);
        document.add(signature);
        document.close();
        return out.toByteArray();
    }

    /**
     * Older invoice merges kept original row amounts while the new invoice total
     * represented only the outstanding debt. Scale those rows to the saved total
     * so the printable document balances; normal invoices are unchanged.
     */
    private List<BigDecimal> displayedAmounts(List<InvoiceOrderViewDTO> orders, BigDecimal invoiceTotal) {
        List<BigDecimal> amounts = orders.stream()
                .map(order -> order.getAmount() == null ? BigDecimal.ZERO : order.getAmount())
                .toList();
        if (amounts.isEmpty() || invoiceTotal == null || amounts.stream().anyMatch(v -> v.signum() < 0)) {
            return amounts;
        }

        BigDecimal rowsTotal = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rowsTotal.signum() <= 0 || rowsTotal.compareTo(invoiceTotal) == 0) return amounts;
        if (invoiceTotal.signum() < 0) return amounts;
        if (invoiceTotal.compareTo(rowsTotal) < 0) {
            return BillingMath.remaining(amounts, invoiceTotal);
        }

        var adjusted = new ArrayList<>(amounts);
        int last = adjusted.size() - 1;
        adjusted.set(last, adjusted.get(last).add(invoiceTotal.subtract(rowsTotal)));
        return adjusted;
    }

    private String describeOrder(InvoiceOrderViewDTO order, String fallback) {
        String work = fallback == null || fallback.isBlank() ? "Роботи за замовленням" : fallback;
        String details = order.getItems() == null ? "" : order.getItems().stream()
                .map(this::describeItem)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
        return "Замовлення № " + order.getOrderNo() + " — " + work +
                (details.isBlank() ? "" : ": " + details);
    }

    private String describeItem(ItemViewDTO item) {
        StringBuilder result = new StringBuilder();
        if (item.getDescription() != null && !item.getDescription().isBlank()) result.append(item.getDescription());
        if (item.getWidth() != null && item.getHeight() != null) {
            if (!result.isEmpty()) result.append(", ");
            result.append(item.getWidth()).append(" × ").append(item.getHeight()).append(" мм");
        }
        if (item.getQty() != null && item.getQty() > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append("кількість: ").append(item.getQty());
        }
        return result.toString();
    }

    private void add(Document document, String value, Font font) throws DocumentException {
        document.add(new Paragraph(value, font));
    }

    private void addHeader(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
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

    private String status(InvoiceEnums.InvoiceStatus status) {
        if (status == null) return "Не визначено";
        return switch (status) {
            case DRAFT -> "Чернетка";
            case ISSUED -> "Виставлено";
            case PAID -> "Оплачено";
            case PARTIALLY_PAID -> "Частково оплачено";
            case CANCELLED -> "Скасовано";
        };
    }

    private String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("uk", "UA"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
