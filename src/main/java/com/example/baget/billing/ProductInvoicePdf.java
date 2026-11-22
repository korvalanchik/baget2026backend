package com.example.baget.billing;

import com.example.baget.my_company.CompanyDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductInvoicePdf {
    private static final DateTimeFormatter UKRAINIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CompanyDetailsService company;

    public ProductInvoicePdf(CompanyDetailsService company) {
        this.company = company;
    }

    @SuppressWarnings("unchecked")
    public byte[] render(Map<String, Object> invoice) throws Exception {
        var out = new ByteArrayOutputStream();
        // 8 pt top margin (~2.8 mm) to reduce paper use without clipping printer output.
        var doc = new Document(PageSize.A4, 36, 36, 8, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        var base = BaseFont.createFont(
                Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        var font = new Font(base, 10);
        var bold = new Font(base, 11, Font.BOLD);
        var supplier = company.get();

        doc.add(new Paragraph("Постачальник: " + supplier.getFullName(), bold));
        if (supplier.getEdrpou() != null) doc.add(new Paragraph("ЄДРПОУ: " + supplier.getEdrpou(), font));
        if (supplier.getBankAccount() != null) doc.add(new Paragraph(supplier.getBankAccount(), font));
        if (supplier.getAddress() != null) doc.add(new Paragraph(supplier.getAddress(), font));

        Paragraph heading = new Paragraph(
                "Рахунок № " + invoice.get("invoiceNo") + " від " + ukrainianDate(invoice.get("createdAt")),
                bold);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingBefore(6);
        heading.setSpacingAfter(6);
        doc.add(heading);

        doc.add(new Paragraph("Платник: " + invoice.get("payerName"), font));
        if (invoice.get("partnerId") != null) {
            doc.add(new Paragraph("Рахунок фірмі-посереднику за реселлерськими цінами", bold));
        }
        if ("MERGED".equals(invoice.get("lifecycle"))) {
            doc.add(new Paragraph("Архівний рахунок: залишок перенесено до іншого рахунку", bold));
        }

        var table = new PdfPTable(new float[]{2, 4, 2, 2});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setHeaderRows(1);
        for (String header : new String[]{"Замовлення", "Виріб / підстава", "Вартість виробу", "До цього рахунку"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, bold));
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }

        for (var line : (List<Map<String, Object>>) invoice.get("products")) {
            JsonNode snapshot = (JsonNode) line.get("snapshot");
            String label = productDescription(line, snapshot);

            table.addCell(cell(line.get("orderNo").toString(), font, Element.ALIGN_CENTER));
            table.addCell(cell(label, font, Element.ALIGN_LEFT));
            table.addCell(cell(line.get("originalAmount").toString(), font, Element.ALIGN_RIGHT));
            table.addCell(cell(line.get("amount").toString(), font, Element.ALIGN_RIGHT));
        }

        doc.add(table);

        Paragraph total = new Paragraph("Сума рахунку: " + invoice.get("amount") + " грн", font);
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(8);
        doc.add(total);

        BigDecimal debt = (BigDecimal) invoice.get("debt");
        Paragraph balance = new Paragraph(
                "Залишок до оплати на момент друку: " +
                        (debt == null ? BigDecimal.ZERO : debt).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) + " грн",
                bold);
        balance.setAlignment(Element.ALIGN_RIGHT);
        balance.setSpacingBefore(3);
        doc.add(balance);

        if (invoice.get("note") != null && !invoice.get("note").toString().isBlank()) {
            Paragraph note = new Paragraph(invoice.get("note").toString(), font);
            note.setSpacingBefore(8);
            doc.add(note);
        }

        doc.close();
        return out.toByteArray();
    }

    private String productDescription(Map<String, Object> line, JsonNode snapshot) {
        int quantity = snapshot.path("configuration").path("draft").path("form")
                .path("productQuantity").asInt(1);
        BigDecimal width = decimal(snapshot.path("widthMm"));
        BigDecimal height = decimal(snapshot.path("heightMm"));

        // Match the workshop calculation: the visible mat margins enlarge the
        // artwork dimensions once, using the outer dimensions of the finished item.
        JsonNode margins = findMatMargins(snapshot);
        if (margins != null) {
            width = width.add(decimal(margins.path("leftMm"))).add(decimal(margins.path("rightMm")));
            height = height.add(decimal(margins.path("topMm"))).add(decimal(margins.path("bottomMm")));
        }

        String label = "Виріб " + line.get("productId") + " (екз.: " + quantity + ")" +
                "; " + dimension(width) + " × " + dimension(height) + " мм";
        if (line.get("sourceInvoiceNo") != null) {
            label += "; залишок з № " + line.get("sourceInvoiceNo");
        }
        return label;
    }

    private JsonNode findMatMargins(JsonNode snapshot) {
        JsonNode draft = snapshot.path("configuration").path("draft");
        JsonNode formMargins = draft.path("form").path("matMargins");
        if (formMargins.isObject()) return formMargins;

        JsonNode productMargins = draft.path("form").path("product").path("matMargins");
        if (productMargins.isObject()) return productMargins;

        JsonNode components = draft.path("components");
        if (components.isArray()) {
            for (JsonNode component : components) {
                JsonNode margins = component.path("product").path("matMargins");
                if (margins.isObject()) return margins;
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || !value.isNumber()
                ? BigDecimal.ZERO : value.decimalValue();
    }

    private String dimension(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String ukrainianDate(Object value) {
        if (value == null) return "—";
        String text = value.toString();
        if (text.length() < 10) return text;
        try {
            return LocalDate.parse(text.substring(0, 10)).format(UKRAINIAN_DATE);
        } catch (RuntimeException ignored) {
            return text;
        }
    }

    private PdfPCell cell(String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }
}
