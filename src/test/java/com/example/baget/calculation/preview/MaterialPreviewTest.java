package com.example.baget.calculation.preview;

import com.example.baget.calculation.MaterialConsumptionService;
import com.example.baget.parts.*;
import com.example.baget.config.JacksonConfig;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialPreviewTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private Parts part;
    private PartPriceResolver prices;
    private MaterialPreviewService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        part = mock(Parts.class);
        when(part.getPartNo()).thenReturn(28260L);
        when(part.getDescription()).thenReturn("Шнур");
        when(part.getPartKind()).thenReturn(PartKind.MATERIAL);
        when(part.getUnitType()).thenReturn(UnitType.METER);
        when(part.getCalculationMethod()).thenReturn(CalculationMethod.WIDTH_PLUS_ALLOWANCE);
        when(part.getCalculationParams()).thenReturn(Map.of("allowanceMm", new BigDecimal("100")));
        when(part.getListPrice()).thenReturn(6.2);
        when(part.getListPrice_1()).thenReturn(3.1);
        when(part.getListPrice_3()).thenReturn(4.7);
        when(part.getCost()).thenReturn(1.9);
        when(repository.findById(28260L)).thenReturn(Optional.of(part));
        prices = new PartPriceResolver();
        service = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, factory.getValidator());
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new MaterialPreviewController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }
    private Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private MaterialPreviewRequest request() {
        return new MaterialPreviewRequest(28260L, new BigDecimal("600"), null, 2, null);
    }
    private void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test void usesRolePricesAndCounterPriority() {
        decimal("3.1", prices.resolve(part, auth("ROLE_COUNTER", "ROLE_LEVEL2")));
        decimal("6.2", prices.resolve(part, auth("ROLE_LEVEL2")));
        decimal("4.7", prices.resolve(part, auth("ROLE_USER")));
        decimal("1.9", prices.resolve(part, auth("ROLE_ADMIN")));
        decimal("1.9", prices.resolve(part, auth("ROLE_ADMIN", "ROLE_COUNTER", "ROLE_LEVEL2")));
    }
    @Test void adminAmountUsesCostAndMissingCostNeverFallsBack() {
        decimal("2.66", service.preview(request(), auth("ROLE_ADMIN")).amount());
        when(part.getCost()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request(), auth("ROLE_ADMIN", "ROLE_COUNTER"))).getCode());
        when(part.getCost()).thenReturn(0.0);
        decimal("0", service.preview(request(), auth("ROLE_ADMIN")).amount());
        when(part.getCost()).thenReturn(-1.0);
        assertEquals("PRICE_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request(), auth("ROLE_ADMIN"))).getCode());
    }
    @Test void calculatesTotalAndDoesNotWriteToRepository() {
        var result = service.preview(request(), auth("ROLE_LEVEL2"));
        decimal("0.7", result.consumptionPerProduct());
        decimal("1.4", result.totalConsumption());
        decimal("8.68", result.amount());
        verify(repository).findById(28260L);
        verifyNoMoreInteractions(repository);
    }
    @Test void nullPriceFailsButExplicitZeroIsAllowed() {
        when(part.getListPrice()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request(), auth("ROLE_LEVEL2"))).getCode());
        when(part.getListPrice()).thenReturn(0.0);
        decimal("0", service.preview(request(), auth("ROLE_LEVEL2")).amount());
    }
    @Test void missingAndUnconfiguredPartsHaveDifferentErrors() {
        when(repository.findById(28260L)).thenReturn(Optional.empty());
        assertEquals("PART_NOT_FOUND", assertThrows(PreviewException.class,
                () -> service.preview(request(), auth("ROLE_LEVEL2"))).getCode());
        when(repository.findById(28260L)).thenReturn(Optional.of(part));
        when(part.getCalculationMethod()).thenReturn(null);
        assertEquals("PART_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request(), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void moneyRoundsAfterTotalConsumption() {
        when(part.getCalculationMethod()).thenReturn(CalculationMethod.QUANTITY);
        when(part.getUnitType()).thenReturn(UnitType.PIECE);
        when(part.getCalculationParams()).thenReturn(Map.of());
        when(part.getListPrice()).thenReturn(0.335);
        var r = new MaterialPreviewRequest(28260L, null, null, 3, 1);
        decimal("1.01", service.preview(r, auth("ROLE_LEVEL2")).amount());
    }
    @Test void httpIgnoresClientPriceAndUsesServerPrice() throws Exception {
        mvc.perform(post("/api/calculations/material-preview")
                .principal(auth("ROLE_LEVEL2")).contentType("application/json")
                .content("""
                    {"partNo":28260,"widthMm":600,"productQuantity":2,"unitPrice":0.01}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("amount").value(8.68));
    }
    @Test void httpRejectsInvalidCountBeforeRepositoryRead() throws Exception {
        mvc.perform(post("/api/calculations/material-preview")
                .principal(auth("ROLE_LEVEL2")).contentType("application/json")
                .content("""
                    {"partNo":28260,"widthMm":600,"productQuantity":0}
                    """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void httpRejectsMissingRequiredDimension() throws Exception {
        mvc.perform(post("/api/calculations/material-preview")
                .principal(auth("ROLE_LEVEL2")).contentType("application/json")
                .content("""
                    {"partNo":28260,"productQuantity":2}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
    }
    @Test void unauthenticatedCallDoesNotReadParts() throws Exception {
        mvc.perform(post("/api/calculations/material-preview").contentType("application/json")
                .content("""
                    {"partNo":28260,"widthMm":600,"productQuantity":2}
                    """))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/calculations/material-preview")
                .principal(auth("ROLE_LEVEL2")).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_JSON"));
    }
    @Test void fractionalProductQuantityIsNotTruncated() throws Exception {
        mvc.perform(post("/api/calculations/material-preview")
                .principal(auth("ROLE_LEVEL2")).contentType("application/json")
                .content("""
                    {"partNo":28260,"widthMm":600,"productQuantity":1.5}
                    """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void rejectsInvalidUnitsMultiplierForLength() {
        var r = new MaterialPreviewRequest(28260L, new BigDecimal("600"), null, 2, 2);
        assertEquals("INVALID_REQUEST", assertThrows(PreviewException.class,
                () -> service.preview(r, auth("ROLE_LEVEL2"))).getCode());
    }
}
