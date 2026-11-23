package com.example.baget.billing;

import com.example.baget.my_company.CompanyDetails;
import com.example.baget.my_company.CompanyDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductInvoicePdfTest {

    @Test
    void rendersCompactInvoiceWithLocalizedDateAndFinishedMatDimensions() throws Exception {
        CompanyDetailsService companyService = mock(CompanyDetailsService.class);
        CompanyDetails supplier = new CompanyDetails();
        supplier.setFullName("ТОВ Рамарня");
        supplier.setEdrpou("12345678");
        when(companyService.get()).thenReturn(supplier);

        var mapper = new ObjectMapper();
        var snapshot = mapper.readTree("""
                {
                  "widthMm": 300,
                  "heightMm": 400,
                  "configuration": {
                    "draft": {
                      "form": {
                        "productQuantity": 2,
                        "matMargins": {
                          "leftMm": 30,
                          "rightMm": 40,
                          "topMm": 20,
                          "bottomMm": 25
                        }
                      }
                    }
                  }
                }
                """);

        Map<String, Object> line = Map.of(
                "productId", 77L,
                "orderNo", 123L,
                "snapshot", snapshot,
                "originalAmount", new BigDecimal("1200.00"),
                "amount", new BigDecimal("900.00"));

        byte[] pdf = new ProductInvoicePdf(companyService).render(Map.of(
                "invoiceNo", 22112026001L,
                "createdAt", "2026-11-22T15:50:15",
                "payerName", "Клієнт Тест",
                "products", List.of(line),
                "amount", new BigDecimal("900.00"),
                "debt", new BigDecimal("500.00")));

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        assertTrue(text.contains("Рахунок № 22112026001 від 22.11.2026"));
        assertFalse(text.contains("15:50:15"));
        assertTrue(text.contains("370 × 445 мм"));
        assertTrue(text.contains("Залишок до оплати на момент друку"));
        assertFalse(text.contains("Рахунок охоплює лише перелічені вироби"));
        assertFalse(text.contains("Дата:"));
        assertTrue(text.contains("Сума рахунку:"));
    }
    @Test
    void detailedFormatPrintsSavedCompositionAndKeepsInvoiceAmountSeparate() throws Exception {
        CompanyDetailsService companyService = mock(CompanyDetailsService.class);
        CompanyDetails supplier = new CompanyDetails();
        supplier.setFullName("ТОВ Рамарня");
        when(companyService.get()).thenReturn(supplier);

        var mapper = new ObjectMapper();
        var snapshot = mapper.readTree("""
                {
                  "widthMm": 300,
                  "heightMm": 400,
                  "total": 1500.00,
                  "lines": [
                    {
                      "itemNo": 1,
                      "description": "Багет алюмінієвий",
                      "role": "FRAME",
                      "unit": "м",
                      "actual": 4.5,
                      "price": 200.00,
                      "amount": 900.00,
                      "material": true
                    },
                    {
                      "itemNo": 2,
                      "description": "Збирання рами",
                      "role": "ASSEMBLY",
                      "unit": "шт",
                      "actual": 1,
                      "price": 600.00,
                      "amount": 600.00,
                      "material": false
                    }
                  ]
                }
                """);
        Map<String, Object> line = Map.of(
                "productId", 77L,
                "orderNo", 123L,
                "snapshot", snapshot,
                "originalAmount", new BigDecimal("1500.00"),
                "amount", new BigDecimal("1200.00"));

        byte[] pdf = new ProductInvoicePdf(companyService).render(Map.of(
                "invoiceNo", 22112026001L,
                "createdAt", "2026-11-22T15:50:15",
                "payerName", "Клієнт Тест",
                "products", List.of(line),
                "amount", new BigDecimal("1200.00"),
                "debt", new BigDecimal("1200.00")), true);

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        assertTrue(text.contains("Багет алюмінієвий"));
        assertTrue(text.contains("Збирання рами"));
        assertTrue(text.contains("Підтверджена вартість виробу: 1500.00 грн"));
        assertTrue(text.contains("Сума, включена до цього рахунку: 1200.00 грн"));
        assertTrue(text.contains("Сума рахунку: 1200.00 грн"));
    }

}
