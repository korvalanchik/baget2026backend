package com.example.baget.util;

import com.example.baget.my_company.CompanyDetails;
import com.example.baget.my_company.CompanyDetailsService;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class InvoiceServiceUtil {

    private final OrdersRepository ordersRepository;
    private final CompanyDetailsService companyDetailsService;

    public InvoiceServiceUtil(OrdersRepository ordersRepository, CompanyDetailsService companyDetailsService) {
        this.ordersRepository = ordersRepository;
        this.companyDetailsService = companyDetailsService;
    }

    public byte[] generateInvoicePdf(Long invoiceNo) throws IOException {
        List<Orders> orders = ordersRepository.findByRahFacNo(invoiceNo);
        if (orders.isEmpty()) {
            throw new TransactionException("Рахунок-фактура не знайдений. Можливо він не створювався");
        }

        CompanyDetails details = companyDetailsService.get(); // ← МАГІЯ

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont bfBold = BaseFont.createFont(
                Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans_Bold.ttf")).toString(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED);

        BaseFont bfNormal = BaseFont.createFont(
                Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED);

        Font normal = new Font(bfNormal, 10);
        Font bold = new Font(bfBold, 10);

        // --- РЕКВІЗИТИ БЕРЕМО З БД ---
        document.add(new Paragraph("Постачальник: " + details.getFullName(), bold));
        if(details.getEdrpou() != null)
            document.add(new Paragraph("ЄДРПОУ " + details.getEdrpou() + ", тел. " + details.getPhone(), normal));
        if(details.getBankAccount() != null)
            document.add(new Paragraph(details.getBankAccount(), normal));
        if(details.getIpn() != null)
            document.add(new Paragraph("ІПН " + details.getIpn(), normal));
        if (details.getComment() != null)
            document.add(new Paragraph(details.getComment(), normal));
        if(details.getAddress() != null)
            document.add(new Paragraph("Адреса: " + details.getAddress() + "\n\n", normal));

        // Одержувач
        document.add(new Paragraph("Одержувач: " + resolveCustomerName(orders), bold));
        document.add(new Paragraph("Платник: той самий\n\n", normal));

        // Реквізити рахунку
//        LocalDate today = LocalDate.now();
        LocalDate invoiceDate = extractDateFromInvoiceNo(invoiceNo);
        String dateStr = invoiceDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new java.util.Locale("uk"))) + " року";

        Paragraph p = new Paragraph("Рахунок-фактура № " + invoiceNo, bold);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);

        Paragraph d = new Paragraph("від " + dateStr + "\n\n", normal);
        d.setAlignment(Element.ALIGN_CENTER);
        document.add(d);

        // Таблиця
        PdfPTable table = new PdfPTable(new float[]{1, 2, 5, 2, 3, 3});
        table.setWidthPercentage(100);

        PdfPCell h1 = new PdfPCell(new Phrase("№", normal));
        h1.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h1);

        PdfPCell h2 = new PdfPCell(new Phrase("№ замов.", normal));
        h2.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h2);

        PdfPCell h3 = new PdfPCell(new Phrase("Товар, послуга", normal));
        h3.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h3);

        PdfPCell h4 = new PdfPCell(new Phrase("Кількість", normal));
        h4.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h4);

        PdfPCell h5 = new PdfPCell(new Phrase("Ціна без ПДВ, грн", normal));
        h5.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h5);

        PdfPCell h6 = new PdfPCell(new Phrase("Сума без ПДВ, грн", normal));
        h6.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(h6);

        int idx = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (Orders o : orders) {

            BigDecimal price = o.getCalculationRevision()>0 ? o.getProductionTotal() : o.getAmountDueN().add(o.getAmountPaid());

            PdfPCell c1 = new PdfPCell(new Phrase(String.valueOf(idx++), normal));
            c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(c1);

            PdfPCell c2 = new PdfPCell(new Phrase(String.valueOf(o.getOrderNo()), normal));
            c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(c2);

            PdfPCell c3 = new PdfPCell(new Phrase(details.getWorkTitle(), normal));
            c3.setHorizontalAlignment(Element.ALIGN_LEFT);  // текст — ліворуч
            table.addCell(c3);

            PdfPCell c4 = new PdfPCell(new Phrase("1", normal));
            c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(c4);

            PdfPCell c5 = new PdfPCell(new Phrase(String.format("%.2f", price), normal));
            c5.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(c5);

            PdfPCell c6 = new PdfPCell(new Phrase(String.format("%.2f", price), normal));
            c6.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(c6);

            total = total.add(price);

        }
        document.add(table);

        // Підсумок
        Paragraph s = new Paragraph("\nВсього: " + String.format("%.2f грн", total), bold);
        s.setAlignment(Element.ALIGN_RIGHT);
        document.add(s);
        document.add(new Paragraph("Всього на суму: " + MoneyToWordsUA.convert(total), bold));
        document.add(new Paragraph("ПДВ: —\n\n", normal));
        document.add(new Paragraph("Виписав(ла): " + details.getInitials() + " ____________", normal));

        document.close();

        return baos.toByteArray();
    }

    private String resolveCustomerName(List<Orders> orders) {

        // Витягаємо першого клієнта
        Long firstCustomerId = orders.get(0).getCustomer().getCustNo();

        boolean allSame = orders.stream()
                .allMatch(o -> o.getCustomer().getCustNo().equals(firstCustomerId));

        if (allSame) {
            return orders.get(0).getCustomer().getCompany();
        }

        // Інакше — компанія за замовчуванням
        return companyDetailsService.get().getDefaultRecipient();
    }

    private LocalDate extractDateFromInvoiceNo(Long invoiceNo) {
        String s = invoiceNo.toString();

        if (s.length() < 9) {
            throw new IllegalArgumentException("Invalid invoice number: " + s);
        }
        int len = s.length();
        int year = Integer.parseInt(s.substring(len - 7, len - 3));
        int month = Integer.parseInt(s.substring(len - 9, len - 7));
        int day = Integer.parseInt(s.substring(0, len - 9));

        return LocalDate.of(year, month, day);
    }

    public Long generateTodayCode() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String result = today.format(formatter) + "001";
        return Long.parseLong(result);
    }

}
