package com.example.baget.calculation.suspension;

import com.example.baget.calculation.MaterialConsumptionService;
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
import static com.example.baget.calculation.suspension.SuspensionRules.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SuspensionPreviewTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private SuspensionPreviewService service;
    private MockMvc mvc;
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String e, BigDecimal actual) { assertEquals(0, n(e).compareTo(actual)); }
    private UsernamePasswordAuthenticationToken auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private Parts part(long id, boolean cord, double price, double cost) {
        var p = mock(Parts.class);
        when(p.getPartNo()).thenReturn(id);
        when(p.getDescription()).thenReturn("Part " + id);
        when(p.getCalculationMethod()).thenReturn(cord ? CalculationMethod.WIDTH_PLUS_ALLOWANCE : CalculationMethod.QUANTITY);
        when(p.getUnitType()).thenReturn(cord ? UnitType.METER : UnitType.PIECE);
        when(p.getPartKind()).thenReturn(PartKind.MATERIAL);
        when(p.getCalculationParams()).thenReturn(cord ? Map.of("allowanceMm", n("100")) : Map.of());
        when(p.getListPrice()).thenReturn(price);
        when(p.getCost()).thenReturn(cost);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
    private SuspensionPreviewRequest req(String width, int qty, Long dvp, Long mirror, Mode mode,
                                         Long hanger, Integer count, boolean feet, Integer footCount) {
        return new SuspensionPreviewRequest(n(width), qty, dvp, mirror, mode, hanger, count, feet, footCount);
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        part(H01, false, 2.5, .4); part(H04, false, 6.2, .51);
        part(H099, false, 3.8, .27); part(POWER, false, 16.2, 7);
        part(POZZI, false, 20, .7); part(CORD, true, 6.2, 1.9);
        when(repository.findById(DVP)).thenReturn(Optional.of(mock(Parts.class)));
        when(repository.findById(MIRROR)).thenReturn(Optional.of(mock(Parts.class)));
        var prices = new PartPriceResolver();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, factory.getValidator());
        service = new SuspensionPreviewService(repository, materials, prices, factory.getValidator());
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new SuspensionPreviewController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }

    @Test void allBoundaryAndCompatibilityRules() { SuspensionRulesChecks.runAll(); }
    @Test void quantitiesMultiplyOnceAndCordIsOneLengthPerProduct() {
        var r = service.preview(req("400", 3, null, MIRROR, Mode.AUTO, null, 4, false, null), auth("ROLE_LEVEL2"));
        eq("12", r.lines().get(0).calculation().totalConsumption());
        eq("1.5", r.lines().get(1).calculation().totalConsumption());
        eq("54.90", r.total());
        verify(repository, never()).save(any(Parts.class));
    }
    @Test void largeMirrorDoesNotLoadOrChargeCord() {
        var r = service.preview(req("400.001", 1, DVP, MIRROR, Mode.AUTO, null, null, false, null), auth("ROLE_LEVEL2"));
        eq("32.40", r.total());
        assertFalse(r.selection().cordIncluded());
        assertEquals(1, r.lines().size());
        verify(repository, never()).findById(CORD);
    }
    @Test void crocodileAndPozziHaveIndependentCountsAndAdminCost() {
        var request = req("450.1", 3, DVP, null, Mode.AUTO, null, null, true, null);
        eq("157.20", service.preview(request, auth("ROLE_LEVEL2")).total());
        eq("7.26", service.preview(request, auth("ROLE_ADMIN", "ROLE_LEVEL2")).total());
        verify(repository, never()).findById(CORD);
    }
    @Test void pozziAloneAndNoHardwareAreSupported() {
        var r = service.preview(req("299.9", 1, DVP, null, Mode.NONE, null, null, true, null), auth("ROLE_LEVEL2"));
        eq("20.00", r.total());
        assertEquals(POZZI, r.lines().get(0).calculation().partNo());
        var empty = service.preview(req("300", 1, null, null, Mode.NONE, null, null, false, null), auth("ROLE_LEVEL2"));
        assertTrue(empty.lines().isEmpty()); eq("0.00", empty.total());
    }
    @Test void invalidCombinationAndUnsupportedContextFail() {
        assertEquals("INVALID_SUSPENSION_SELECTION", assertThrows(PreviewException.class,
                () -> service.preview(req("300", 1, DVP, null, Mode.MANUAL, H099, null, true, null), auth("ROLE_LEVEL2"))).getCode());
        assertEquals("UNSUPPORTED_SUSPENSION_CONTEXT", assertThrows(PreviewException.class,
                () -> service.preview(req("300", 1, 999L, null, Mode.AUTO, null, null, false, null), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void missingPartAndConflictingMetadataFailWithoutPartialResult() {
        when(repository.findById(H04)).thenReturn(Optional.empty());
        var request = req("300", 1, DVP, null, Mode.AUTO, null, null, false, null);
        assertEquals("COMPOSITION_PART_MISSING", assertThrows(PreviewException.class,
                () -> service.preview(request, auth("ROLE_LEVEL2"))).getCode());
        var p = part(H04, false, 6.2, .51);
        when(p.getPartKind()).thenReturn(PartKind.WORK);
        assertEquals("INVALID_SUSPENSION_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(request, auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void nullPriceMissingContextAndUnauthenticatedRequestsFail() {
        var request = req("300", 1, DVP, null, Mode.AUTO, null, null, false, null);
        when(repository.findById(DVP)).thenReturn(Optional.empty());
        assertThrows(PreviewException.class, () -> service.preview(request, auth("ROLE_LEVEL2")));
        when(repository.findById(DVP)).thenReturn(Optional.of(mock(Parts.class)));
        var p = part(H04, false, 6.2, .51); when(p.getListPrice()).thenReturn(null);
        assertThrows(PreviewException.class, () -> service.preview(request, auth("ROLE_LEVEL2")));
        assertThrows(PreviewException.class, () -> service.preview(request, null));
    }
    @Test void httpUsesServerPricesAndPreservesDecimalWidth() throws Exception {
        mvc.perform(post("/api/calculations/suspension-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"widthMm":149.999,"productQuantity":1,"backingPartNo":4460,"hangerMode":"AUTO",
                 "includePozzi":false,"total":0,"unitPrice":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selection.hangerPartNo").value(27580))
                .andExpect(jsonPath("$.total").value(2.5));
    }
    @Test void httpRejectsZeroQuantityAndUnknownMode() throws Exception {
        mvc.perform(post("/api/calculations/suspension-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"widthMm":300,"productQuantity":1,"hangerMode":"AUTO","includePozzi":false,"hangersPerProduct":0}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/calculations/suspension-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"widthMm":300,"productQuantity":1,"hangerMode":"UNKNOWN","includePozzi":false}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }
}
