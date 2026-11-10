package com.example.baget.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyDetailsAdminServiceTest {

    @Mock
    private JdbcTemplate db;

    @InjectMocks
    private CompanyDetailsAdminService service;

    @Test
    void getWarnsWhenCompanyTaxIdentifiersAreMissing() throws Exception {
        stubCompanyRow("ФОП Приклад", null, null, null);

        CompanyDetailsAdminDTO result = service.get();

        assertFalse(result.readyForReceipts());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("податковий номер"));
    }

    @Test
    void getIsReadyWhenVatTaxIdentifierIsPresent() throws Exception {
        stubCompanyRow("ФОП Приклад", null, "123456789012", "Миколаїв");

        CompanyDetailsAdminDTO result = service.get();

        assertTrue(result.readyForReceipts());
        assertTrue(result.warnings().isEmpty());
        assertEquals("123456789012", result.ipn());
    }

    private void stubCompanyRow(String fullName, String edrpou, String ipn, String address) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString("full_name")).thenReturn(fullName);
        when(rs.getString("edrpou")).thenReturn(edrpou);
        when(rs.getString("ipn")).thenReturn(ipn);
        when(rs.getString("address")).thenReturn(address);

        when(db.query(anyString(),
                ArgumentMatchers.<ResultSetExtractor<CompanyDetailsAdminDTO>>any(),
                eq(1L)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<CompanyDetailsAdminDTO> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });
    }
}
