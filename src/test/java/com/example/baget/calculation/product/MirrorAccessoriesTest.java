package com.example.baget.calculation.product;

import com.example.baget.calculation.MaterialConsumptionService;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.suspension.*;
import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.mounting.MountingPreviewService;
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

class MirrorAccessoriesTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private ProductPreviewService service;
    private MockMvc mvc;
    private final Map<Long, Parts> catalog = new HashMap<>();
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String e, BigDecimal actual) { assertEquals(0, n(e).compareTo(actual)); }
    private UsernamePasswordAuthenticationToken auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private void part(long id, CalculationMethod method, UnitType unit,
                      Map<String, BigDecimal> params, double price, double cost) {
        var p = mock(Parts.class);
        when(p.getPartNo()).thenReturn(id); when(p.getDescription()).thenReturn("Part " + id);
        when(p.getCalculationMethod()).thenReturn(method); when(p.getUnitType()).thenReturn(unit);
        when(p.getPartKind()).thenReturn(PartKind.MATERIAL); when(p.getCalculationParams()).thenReturn(params);
        when(p.getListPrice()).thenReturn(price); when(p.getCost()).thenReturn(cost);
        when(repository.findById(id)).thenReturn(Optional.of(p)); catalog.put(id, p);
    }
    private SuspensionSelection auto() {
        return new SuspensionSelection(SuspensionRules.Mode.AUTO, null, null, false, null);
    }
    private ProductPreviewRequest request(String w, String h, int qty, Long backing, SuspensionSelection choice) {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.MIRROR_IN_FRAME,
                n(w), n(h), qty, 29340L, 27760L, null, backing, choice);
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory(); repository = mock(PartsRepository.class);
        part(29340, CalculationMethod.FRAME_PROFILE, UnitType.METER, Map.of(), 102.5, 25);
        when(catalog.get(29340L).getProfilWidth()).thenReturn(.024);
        part(27760, CalculationMethod.AREA, UnitType.SQUARE_METER, Map.of(), 1297.5, 150);
        part(28540, CalculationMethod.PERIMETER, UnitType.METER, Map.of(), 20, 10);
        part(4460, CalculationMethod.AREA, UnitType.SQUARE_METER, Map.of(), 201.2, 85);
        part(14460, CalculationMethod.QUANTITY, UnitType.PIECE, Map.of(), 3.8, .27);
        part(29650, CalculationMethod.QUANTITY, UnitType.PIECE, Map.of(), 16.2, 7);
        part(23080, CalculationMethod.QUANTITY, UnitType.PIECE, Map.of(), 20, .7);
        part(28260, CalculationMethod.WIDTH_PLUS_ALLOWANCE, UnitType.METER, Map.of("allowanceMm", n("100")), 6.2, 1.9);
        var prices = new PartPriceResolver(); var validator = factory.getValidator();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, validator);
        var suspensions = new SuspensionPreviewService(repository, materials, prices, validator);
        var accessories = new MirrorAccessoriesComposer(repository, materials, suspensions);
        service = new ProductPreviewService(new MirrorFrameRule(27760, 28540), repository, materials, prices,
                validator, mock(UnderframePreviewComposer.class), accessories);
        var estimate = new EstimatePreviewService(service, mock(MountingPreviewService.class), prices, validator);
        var builder = new Jackson2ObjectMapperBuilder(); new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new ProductPreviewController(service), new EstimatePreviewController(estimate))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }

    @Test void oldRequestStillHasOnlyFrameMirrorAndGlue() {
        var old = new ProductPreviewRequest(ProductPreviewRequest.ProductType.MIRROR_IN_FRAME,
                n("400"), n("600"), 1, 29340L, 27760L);
        var r = service.preview(old, auth("ROLE_LEVEL2"));
        assertEquals(3, r.lines().size()); eq("576.08", r.total());
        verify(repository, never()).findById(4460L); verify(repository, never()).findById(14460L);
    }
    @Test void dvpAndSmallMirrorUse099AndCordOnce() {
        var r = service.preview(request("400", "600", 1, 4460L, auto()), auth("ROLE_LEVEL2"));
        assertEquals(List.of("FRAME", "MIRROR", "MIRROR_GLUE", "BACKING", "HANGER", "CORD"),
                r.lines().stream().map(ProductPreviewResponse.Line::role).toList());
        eq(".24", r.lines().get(3).calculation().totalConsumption());
        eq(".5", r.lines().get(5).calculation().totalConsumption()); eq("635.07", r.total());
        verify(repository, never()).save(any(Parts.class));
    }
    @Test void largerMirrorUsesPowerHangerWithoutCordEvenWithDvp() {
        var r = service.preview(request("600", "800", 1, 4460L, auto()), auth("ROLE_LEVEL2"));
        assertEquals(5, r.lines().size());
        assertEquals(29650L, r.lines().get(4).calculation().partNo()); eq("1114.46", r.total());
        verify(repository, never()).findById(28260L);
    }
    @Test void quantityAndAdminCostAreAppliedOnceAcrossAllComponents() {
        var r = service.preview(request("600", "800", 2, 4460L, auto()), auth("ROLE_ADMIN", "ROLE_LEVEL2"));
        eq(".96", r.lines().get(3).calculation().totalConsumption());
        eq("4", r.lines().get(4).calculation().totalConsumption()); eq("459.20", r.total());
    }
    @Test void backingIsOptionalAndQuantityOverrideIsPreserved() {
        var choice = new SuspensionSelection(SuspensionRules.Mode.AUTO, null, 1, false, null);
        var r = service.preview(request("600", "800", 1, null, choice), auth("ROLE_LEVEL2"));
        assertEquals(4, r.lines().size()); eq("1", r.lines().get(3).calculation().totalConsumption());
        eq("1001.68", r.total()); verify(repository, never()).findById(4460L);
    }
    @Test void pozziAloneUsesDvpAndNoneWithoutCord() {
        var choice = new SuspensionSelection(SuspensionRules.Mode.NONE, null, null, true, null);
        var r = service.preview(request("400", "600", 1, 4460L, choice), auth("ROLE_LEVEL2"));
        assertEquals("POZZI", r.lines().get(4).role()); eq("2", r.lines().get(4).calculation().totalConsumption());
        eq("664.37", r.total()); verify(repository, never()).findById(28260L);
    }
    @Test void roundsAllRawLinesTogetherInsteadOfAddingModuleTotals() {
        when(catalog.get(29340L).getListPrice()).thenReturn(.002);
        when(catalog.get(14460L).getListPrice()).thenReturn(.002);
        for (long id : new long[]{27760,28540,28260}) when(catalog.get(id).getListPrice()).thenReturn(0.0);
        var r = service.preview(request("400", "600", 1, null, auto()), auth("ROLE_LEVEL2"));
        eq(".01", r.total()); eq("0", r.linesSubtotal()); eq(".01", r.roundingAdjustment());
    }
    @Test void crocodilesAreRejectedBecauseMirrorContextCannotBeOverridden() {
        var choice = new SuspensionSelection(SuspensionRules.Mode.MANUAL, 14450L, 1, false, null);
        assertEquals("INVALID_SUSPENSION_SELECTION", assertThrows(PreviewException.class,
                () -> service.preview(request("150", "200", 1, 4460L, choice), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void badBackingMissingPartOrWrongMetadataAreRejected() {
        assertEquals("UNSUPPORTED_BACKING_PART", assertThrows(PreviewException.class,
                () -> service.preview(request("400", "600", 1, 999L, null), auth("ROLE_LEVEL2"))).getCode());
        when(repository.findById(4460L)).thenReturn(Optional.empty());
        assertEquals("COMPOSITION_PART_MISSING", assertThrows(PreviewException.class,
                () -> service.preview(request("400", "600", 1, 4460L, null), auth("ROLE_LEVEL2"))).getCode());
        when(repository.findById(4460L)).thenReturn(Optional.of(catalog.get(4460L)));
        when(catalog.get(4460L).getCalculationMethod()).thenReturn(CalculationMethod.AREA_WITH_MARGIN);
        assertEquals("INVALID_BACKING_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(request("400", "600", 1, 4460L, null), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void addonsAreNotSilentlyIgnoredForUnderframe() {
        var r = new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n("400"), n("600"), 1, null, null, 29720L, 4460L, auto());
        assertEquals("INVALID_PRODUCT_SELECTION", assertThrows(PreviewException.class,
                () -> service.preview(r, auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void nestedValidationRejectsMissingModeBeforeCalculating() {
        var choice = new SuspensionSelection(null, null, null, false, null);
        assertEquals("INVALID_REQUEST", assertThrows(PreviewException.class,
                () -> service.preview(request("400", "600", 1, null, choice), auth("ROLE_LEVEL2"))).getCode());
        verify(repository, never()).findById(anyLong());
    }
    @Test void estimateHttpIncludesAccessoriesAndIgnoresNestedContextSpoofing() throws Exception {
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"items":[{"clientItemId":"mirror-1","kind":"PRODUCT","product":{
                  "productType":"MIRROR_IN_FRAME","widthMm":600,"heightMm":800,"productQuantity":1,
                  "framePartNo":29340,"mirrorPartNo":27760,"backingPartNo":4460,
                  "suspension":{"hangerMode":"AUTO","includePozzi":false,"widthMm":100,
                    "productQuantity":99,"mirrorPartNo":null,"total":0}}}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1114.46))
                .andExpect(jsonPath("$.items[0].product.lines[4].calculation.partNo").value(29650))
                .andExpect(jsonPath("$.items[0].product.lines[4].calculation.totalConsumption").value(2));
    }
}
