package com.example.baget.calculation.product;

import com.example.baget.calculation.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.parts.*;
import com.example.baget.config.JacksonConfig;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.converter.json.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnderframePreviewTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private ProductPreviewService service;
    private Parts underframe;
    private Parts rail;
    private MockMvc mvc;
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, n(expected).compareTo(actual)); }
    private UsernamePasswordAuthenticationToken auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private ProductPreviewRequest request(String width, String height, int quantity) {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n(width), n(height), quantity, null, null, 29720L);
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        underframe = mock(Parts.class);
        when(underframe.getPartNo()).thenReturn(29720L);
        when(underframe.getDescription()).thenReturn("Підрамник");
        when(underframe.getPartKind()).thenReturn(PartKind.MATERIAL);
        when(underframe.getUnitType()).thenReturn(UnitType.METER);
        when(underframe.getCalculationMethod()).thenReturn(CalculationMethod.PERIMETER);
        when(underframe.getCalculationParams()).thenReturn(Map.of("railWidthMm", n("46")));
        when(underframe.getListPrice()).thenReturn(82.5);
        when(underframe.getListPrice_1()).thenReturn(61.2);
        when(underframe.getCost()).thenReturn(32.0);
        when(repository.findById(29720L)).thenReturn(Optional.of(underframe));
        rail = mock(Parts.class);
        when(rail.getPartNo()).thenReturn(28030L);
        when(rail.getDescription()).thenReturn("Рейка 46х13");
        when(rail.getInQuality()).thenReturn(2.0);
        when(rail.getListPrice()).thenReturn(110.0);
        when(rail.getListPrice_1()).thenReturn(75.0);
        when(rail.getCost()).thenReturn(44.0);
        when(repository.findById(28030L)).thenReturn(Optional.of(rail));
        var prices = new PartPriceResolver();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, factory.getValidator());
        var composer = new UnderframePreviewComposer(repository, materials, prices, new UnderframeCrossbarService(28030));
        service = new ProductPreviewService(new MirrorFrameRule(27760, 28540), repository,
                materials, prices, factory.getValidator(), composer, mock(MirrorAccessoriesComposer.class));
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new ProductPreviewController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }

    @Test void pricesPerimeterAndTwoCrossbarsExactlyOnce() {
        var r = service.preview(request("1200", "1500", 2), auth("ROLE_LEVEL2"));
        assertEquals(2, r.lines().size());
        eq("10.8", r.lines().get(0).calculation().totalConsumption());
        var line = r.lines().get(1);
        assertEquals("CROSSBAR", line.role());
        assertEquals(28030L, line.calculation().partNo());
        assertNull(line.calculation().calculationMethod());
        assertEquals(2, line.segmentsPerProduct().size());
        eq("5.032", line.calculation().totalConsumption());
        eq("553.52", line.calculation().amount());
        eq("1444.52", r.total());
        verify(repository, never()).save(any(Parts.class));
    }
    @Test void adminCostWinsOverCounterForBothLines() {
        eq("567.01", service.preview(request("1200", "1500", 2), auth("ROLE_ADMIN", "ROLE_COUNTER")).total());
    }
    @Test void smallUnderframeDoesNotReadOrPriceRail() {
        var r = service.preview(request("600", "800", 2), auth("ROLE_LEVEL2"));
        assertEquals(1, r.lines().size());
        eq("462", r.total());
        verify(repository, never()).findById(28030L);
    }
    @Test void thresholdCreatesOneCrossbar() {
        var r = service.preview(request("1000", "800", 1), auth("ROLE_LEVEL2"));
        assertEquals(1, r.lines().get(1).segmentsPerProduct().size());
        eq("374.88", r.total());
    }
    @Test void selectedRailWidthChangesLength() {
        when(underframe.getCalculationParams()).thenReturn(Map.of("railWidthMm", n("56")));
        var r = service.preview(request("1200", "1500", 2), auth("ROLE_LEVEL2"));
        eq("4.952", r.lines().get(1).calculation().totalConsumption());
    }
    @Test void missingRailOrPriceFailsEntirePreview() {
        when(repository.findById(28030L)).thenReturn(Optional.empty());
        assertEquals("COMPOSITION_PART_MISSING", assertThrows(PreviewException.class,
                () -> service.preview(request("1200", "1500", 1), auth("ROLE_LEVEL2"))).getCode());
        when(repository.findById(28030L)).thenReturn(Optional.of(rail));
        when(rail.getListPrice()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request("1200", "1500", 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void conflictingRailMetadataFails() {
        when(rail.getUnitType()).thenReturn(UnitType.PIECE);
        assertEquals("INVALID_CROSSBAR_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(request("1200", "1500", 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void impossibleGeometryFails() {
        assertEquals("INVALID_UNDERFRAME_GEOMETRY_OR_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(request("1000", "92", 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void mixedProductFieldsAreRejectedBeforeReadingParts() {
        var r = new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n("1200"), n("1500"), 1, 29340L, null, 29720L);
        assertThrows(PreviewException.class, () -> service.preview(r, auth("ROLE_LEVEL2")));
        verifyNoInteractions(repository);
    }
    @Test void httpAcceptsUnderframeJsonAndIgnoresClientRailAndPrice() throws Exception {
        mvc.perform(post("/api/calculations/product-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                  {"productType":"UNDERFRAME","widthMm":1200,"heightMm":1500,
                   "productQuantity":2,"underframePartNo":29720,"crossbarPartNo":10,"unitPrice":0}
                  """))
                .andExpect(status().isOk()).andExpect(jsonPath("total").value(1444.52))
                .andExpect(jsonPath("lines[1].calculation.partNo").value(28030));
    }
    @Test void missingSelectionReturns400() throws Exception {
        mvc.perform(post("/api/calculations/product-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                  {"productType":"UNDERFRAME","widthMm":1200,"heightMm":1500,"productQuantity":2}
                  """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PRODUCT_SELECTION"));
        verifyNoInteractions(repository);
    }
    @Test void anonymousRequestDoesNotReadParts() {
        assertThrows(PreviewException.class, () -> service.preview(request("1200", "1500", 2), null));
        verifyNoInteractions(repository);
    }
}
