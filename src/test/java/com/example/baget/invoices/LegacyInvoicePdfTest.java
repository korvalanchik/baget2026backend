package com.example.baget.invoices;

import com.example.baget.items.ItemViewDTO;
import com.example.baget.my_company.CompanyDetails;
import com.example.baget.my_company.CompanyDetailsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyInvoicePdfTest {

    @Test
    void rendersPdfFromSavedInvoiceAndOrderAllocation() throws Exception {
        CompanyDetailsService companyService = mock(CompanyDetailsService.class);
        CompanyDetails details = new CompanyDetails();
        details.setFullName("ТОВ Приклад");
        details.setEdrpou("12345678");
        details.setBankAccount("UA000000000000000000000000000");
        details.setAddress("Миколаїв");
        details.setWorkTitle("Послуги багетної майстерні");
        details.setInitials("І.І.");
        when(companyService.get()).thenReturn(details);

        InvoiceViewDTO invoice = new InvoiceViewDTO();
        invoice.setInvoiceId(7L);
        invoice.setInvoiceNo(21102026001L);
        invoice.setCustomerName("Клієнт Тест");
        invoice.setType(InvoiceEnums.InvoiceType.CONSOLIDATED);
        invoice.setStatus(InvoiceEnums.InvoiceStatus.ISSUED);
        invoice.setTotalAmount(new BigDecimal("1250.00"));

        InvoiceOrderViewDTO order = new InvoiceOrderViewDTO();
        order.setOrderNo(456L);
        order.setAmount(new BigDecimal("1250.00"));
        ItemViewDTO item = new ItemViewDTO();
        item.setDescription("Багет дерев'яний");
        item.setWidth(300d);
        item.setHeight(400d);
        item.setQty(1d);
        order.setItems(List.of(item));
        invoice.setOrders(List.of(order));

        byte[] pdf = new LegacyInvoicePdf(companyService).render(
                invoice, "Платник Тест", "Примітка", "ACTIVE", new BigDecimal("500.00"));

        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }
}
