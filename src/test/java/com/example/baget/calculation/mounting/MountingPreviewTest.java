package com.example.baget.calculation.mounting;

import com.example.baget.calculation.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
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
import static com.example.baget.calculation.mounting.MountingPreviewRequest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MountingPreviewTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private MountingPreviewService service;
    private Parts canvas;
    private MockMvc mvc;
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, n(expected).compareTo(actual)); }
    private UsernamePasswordAuthenticationToken auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private Parts part(long id, CalculationMethod method, UnitType unit, PartKind kind,
            Map<String, BigDecimal> params, double price, double cost) {
        var p = mock(Parts.class);
        when(p.getPartNo()).thenReturn(id);
        when(p.getDescription()).thenReturn("Part " + id);
        when(p.getCalculationMethod()).thenReturn(method);
        when(p.getUnitType()).thenReturn(unit);
        when(p.getPartKind()).thenReturn(kind);
        when(p.getCalculationParams()).thenReturn(params);
        when(p.getListPrice()).thenReturn(price);
        when(p.getCost()).thenReturn(cost);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
    private MountingPreviewRequest underframe(CanvasType type, Long canvasId, int qty) {
        return new MountingPreviewRequest(MountingType.UNDERFRAME, n("600"), n("800"), qty, type, canvasId, 29720L);
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        part(29720, CalculationMethod.PERIMETER, UnitType.METER, PartKind.MATERIAL,
                Map.of("railWidthMm", n("46")), 82.5, 32);
        part(5130, CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER, PartKind.MATERIAL,
                Map.of("marginWidthMm", n("10.0"), "marginHeightMm", n("10")), 187.5, 50);
        part(33370, CalculationMethod.FIXED, UnitType.PIECE, PartKind.WORK, Map.of(), 46.2, 0);
        part(5140, CalculationMethod.PERIMETER, UnitType.METER, PartKind.WORK, Map.of(), 20, .06);
        part(5150, CalculationMethod.PERIMETER, UnitType.METER, PartKind.WORK, Map.of(), 17.5, .06);
        canvas = part(27970, CalculationMethod.AREA, UnitType.SQUARE_METER, PartKind.MATERIAL, Map.of(), 602.5, 335);
        var rail = part(28030, null, null, null, null, 110, 44);
        when(rail.getInQuality()).thenReturn(2.0);
        var prices = new PartPriceResolver();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, factory.getValidator());
        var composer = new UnderframePreviewComposer(repository, materials, prices, new UnderframeCrossbarService(28030));
        service = new MountingPreviewService(repository, materials, prices, composer, factory.getValidator());
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new MountingPreviewController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }

    @Test void printUses5140And100MmNotBlankTariff() {
        var r = service.preview(underframe(CanvasType.PRINT, 27970L, 1), auth("ROLE_LEVEL2"));
        assertEquals(List.of(29720L, 5140L, 27970L), r.lines().stream().map(l -> l.calculation().partNo()).toList());
        eq("0.63", r.lines().get(2).calculation().totalConsumption());
        eq("2.8", r.lines().get(1).calculation().totalConsumption());
        eq("666.58", r.total());
        verify(repository, never()).findById(5150L);
        verify(repository, never()).findById(33370L);
        verify(repository, never()).save(any(Parts.class));
        verify(canvas, never()).setCalculationParams(any());
    }
    @Test void blankUses5150And80Mm() {
        var r = service.preview(underframe(CanvasType.BLANK, 27970L, 1), auth("ROLE_LEVEL2"));
        assertEquals(5150L, r.lines().get(1).calculation().partNo());
        eq("0.5984", r.lines().get(2).calculation().totalConsumption());
        eq("640.54", r.total());
    }
    @Test void existingImageUses5140ButOnly80Mm() {
        var r = service.preview(underframe(CanvasType.WITH_IMAGE, 27970L, 1), auth("ROLE_LEVEL2"));
        assertEquals(5140L, r.lines().get(1).calculation().partNo());
        eq("0.5984", r.lines().get(2).calculation().totalConsumption());
        eq("647.54", r.total());
    }
    @Test void quantityMultipliesOnceAndTotalRoundsOnlyAtEnd() {
        var r = service.preview(underframe(CanvasType.PRINT, 27970L, 2), auth("ROLE_LEVEL2"));
        eq("1.26", r.lines().get(2).calculation().totalConsumption());
        eq("1333.15", r.total());
        eq("0", r.roundingAdjustment());
        eq(r.total().toPlainString(), r.linesSubtotal().add(r.roundingAdjustment()));
    }
    @Test void customerCanvasIsNotCharged() {
        var r = service.preview(underframe(CanvasType.PRINT, null, 1), auth("ROLE_LEVEL2"));
        assertEquals(2, r.lines().size());
        eq("287", r.total());
        verify(repository, never()).findById(27970L);
    }
    @Test void noMountingMeansNoMarginEvenForPrint() {
        var request = new MountingPreviewRequest(MountingType.NONE, n("600.1"), n("800.1"), 1, CanvasType.PRINT, 27970L, null);
        var r = service.preview(request, auth("ROLE_LEVEL2"));
        assertEquals(1, r.lines().size());
        eq("0.481401", r.lines().get(0).calculation().totalConsumption());
        verify(repository, never()).findById(5140L);
    }
    @Test void planeIncludesMaterialAndOneWorkPerProduct() {
        var request = new MountingPreviewRequest(MountingType.PLANE, n("600"), n("800"), 2, null, null, null);
        var r = service.preview(request, auth("ROLE_LEVEL2"));
        assertEquals(List.of(5130L, 33370L), r.lines().stream().map(l -> l.calculation().partNo()).toList());
        eq("0.9882", r.lines().get(0).calculation().totalConsumption());
        eq("2", r.lines().get(1).calculation().totalConsumption());
        eq("277.69", r.total());
        verify(repository, never()).findById(5140L);
    }
    @Test void adminUsesCostForEveryLineIncludingWork() {
        eq("300.82", service.preview(underframe(CanvasType.PRINT, 27970L, 1), auth("ROLE_ADMIN", "ROLE_LEVEL2")).total());
    }
    @Test void crossbarsAreIncludedOnce() {
        var r = service.preview(new MountingPreviewRequest(MountingType.UNDERFRAME, n("1200"), n("1500"),
                2, CanvasType.PRINT, null, 29720L), auth("ROLE_LEVEL2"));
        assertEquals(3, r.lines().size());
        eq("5.032", r.lines().get(1).calculation().totalConsumption());
        eq("1660.52", r.total());
    }
    @Test void mixedSelectionFailsBeforeReadingParts() {
        assertThrows(PreviewException.class, () -> service.preview(new MountingPreviewRequest(
                MountingType.PLANE, n("600"), n("800"), 1, CanvasType.PRINT, null, null), auth("ROLE_LEVEL2")));
        verify(repository, never()).findById(anyLong());
    }
    @Test void missingOrUnconfiguredWorkFails() {
        when(repository.findById(5140L)).thenReturn(Optional.empty());
        assertEquals("COMPOSITION_PART_MISSING", assertThrows(PreviewException.class,
                () -> service.preview(underframe(CanvasType.PRINT, null, 1), auth("ROLE_LEVEL2"))).getCode());
        part(5140, CalculationMethod.PERIMETER, UnitType.METER, PartKind.MATERIAL, Map.of(), 20, .06);
        assertEquals("INVALID_MOUNTING_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(underframe(CanvasType.PRINT, null, 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void canvasPriceCannotBeNullAndMirrorCannotBeCanvas() {
        when(canvas.getListPrice()).thenReturn(null);
        assertThrows(PreviewException.class, () -> service.preview(underframe(CanvasType.PRINT, 27970L, 1), auth("ROLE_LEVEL2")));
        assertEquals("UNSUPPORTED_CANVAS_PART", assertThrows(PreviewException.class,
                () -> service.preview(underframe(CanvasType.PRINT, 27760L, 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void authenticationAndRequiredCanvasTypeAreEnforced() {
        assertThrows(PreviewException.class, () -> service.preview(underframe(CanvasType.PRINT, null, 1), null));
        assertEquals("INVALID_MOUNTING_SELECTION", assertThrows(PreviewException.class,
                () -> service.preview(underframe(null, null, 1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void fractionsRoundUpBeforeMarginAndInvalidQuantityIsRejected() {
        var r = new MountingPreviewRequest(MountingType.UNDERFRAME, n("600.1"), n("800.1"),
                1, CanvasType.PRINT, 27970L, 29720L);
        eq(".631601", service.preview(r, auth("ROLE_LEVEL2")).lines().get(2).calculation().consumptionPerProduct());
        assertEquals("INVALID_REQUEST", assertThrows(PreviewException.class,
                () -> service.preview(underframe(CanvasType.PRINT, null, 0), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void httpAcceptsStructuredRequestIgnoresClientPriceAndRejectsInvalidEnum() throws Exception {
        mvc.perform(post("/api/calculations/mounting-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"mountingType":"UNDERFRAME","widthMm":600,"heightMm":800,"productQuantity":1,
                 "canvasType":"PRINT","canvasPartNo":27970,"underframePartNo":29720,"unitPrice":0,"total":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(666.58));
        mvc.perform(post("/api/calculations/mounting-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"mountingType":"UNDERFRAME","widthMm":600,"heightMm":800,"productQuantity":1,
                 "canvasType":"UNKNOWN","underframePartNo":29720}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }
}
