package com.example.baget.calculation.product;

import com.example.baget.calculation.*;
import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.mounting.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.suspension.*;
import com.example.baget.config.JacksonConfig;
import com.example.baget.parts.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.http.converter.json.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses real calculators, validation, composition, totals and HTTP serialization. */
class SheetCompositionTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private ProductPreviewService products;
    private SuspensionPreviewService suspensions;
    private EstimatePreviewService estimates;
    private MockMvc mvc;
    private final Map<Long, Parts> catalog = new HashMap<>();
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, n(expected).compareTo(actual)); }
    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
    private void part(long id, CalculationMethod method, UnitType unit, double price) {
        var p = mock(Parts.class);
        when(p.getPartNo()).thenReturn(id); when(p.getDescription()).thenReturn("Part " + id);
        when(p.getPartKind()).thenReturn(PartKind.MATERIAL); when(p.getCalculationMethod()).thenReturn(method);
        when(p.getUnitType()).thenReturn(unit); when(p.getCalculationParams()).thenReturn(Map.of());
        when(p.getListPrice()).thenReturn(price); when(p.getCost()).thenReturn(price / 2);
        when(p.getListPrice_1()).thenReturn(price / 4); when(p.getListPrice_3()).thenReturn(price * 2);
        when(repository.findById(id)).thenReturn(Optional.of(p)); catalog.put(id, p);
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory(); repository = mock(PartsRepository.class);
        part(29340, CalculationMethod.FRAME_PROFILE, UnitType.METER, 100);
        when(catalog.get(29340L).getProfilWidth()).thenReturn(.02);
        for (long id : new long[]{12670,27540,27510,27470,27520,4460,5160,28010,29500,27760})
            part(id, CalculationMethod.AREA, UnitType.SQUARE_METER, 100);
        part(28540, CalculationMethod.PERIMETER, UnitType.METER, 10);
        for (long id : new long[]{27580,14450,14460,29650,23080})
            part(id, CalculationMethod.QUANTITY, UnitType.PIECE, 10);
        part(28260, CalculationMethod.WIDTH_PLUS_ALLOWANCE, UnitType.METER, 10);
        when(catalog.get(28260L).getCalculationParams()).thenReturn(Map.of("allowanceMm", n("100")));
        part(5130, CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER, 100);
        when(catalog.get(5130L).getCalculationParams()).thenReturn(Map.of("marginWidthMm", n("10"), "marginHeightMm", n("10")));
        part(33370, CalculationMethod.FIXED, UnitType.PIECE, 10);
        when(catalog.get(33370L).getPartKind()).thenReturn(PartKind.WORK);
        var validator = factory.getValidator(); var prices = new PartPriceResolver();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, validator);
        suspensions = new SuspensionPreviewService(repository, materials, prices, validator);
        var underframes = new UnderframePreviewComposer(repository, materials, prices, new UnderframeCrossbarService(28030));
        products = new ProductPreviewService(new MirrorFrameRule(27760,28540), repository, materials, prices,
                validator, underframes, new MirrorAccessoriesComposer(repository,materials,suspensions));
        estimates = new EstimatePreviewService(products, new MountingPreviewService(repository,materials,prices,underframes,validator), prices, validator);
        var builder = new Jackson2ObjectMapperBuilder(); new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new ProductPreviewController(products), new EstimatePreviewController(estimates),
                new SuspensionPreviewController(suspensions))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }
    private ProductPreviewRequest request(String width, String height, int qty, Long glass, Integer glassLayers,
            Long backing, Integer backingLayers, Long mat, Integer matLayers, MatMargins margins) {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.FRAMED_ARTWORK,n(width),n(height),qty,
                29340L,null,null,backing,new SuspensionSelection(SuspensionRules.Mode.AUTO,null,null,false,null),
                glass,glassLayers,backingLayers,mat,matLayers,margins);
    }
    private MatMargins margins() { return new MatMargins(n("10"), n("20"), n("30"), n("40")); }
    private MaterialPreviewResponse line(ProductPreviewResponse r, String role) {
        return r.lines().stream().filter(l -> l.role().equals(role)).findFirst().orElseThrow().calculation();
    }
    @Test void marginsAndLayersMultiplySheetsOnlyAndUseOuterWidthForHangers() {
        var request = request("430","600",3,27540L,2,27520L,3,5160L,2,margins());
        var r = products.preview(request,auth("ROLE_LEVEL2"));
        // Outer 460 x 670; one sheet .3082 m2; frame 2.42 m. Width >450 needs two H04.
        eq("2.42",line(r,"FRAME").consumptionPerProduct());
        eq(".6164",line(r,"GLASS").consumptionPerProduct()); eq("1.8492",line(r,"GLASS").totalConsumption());
        eq("2.7738",line(r,"BACKING").totalConsumption()); eq("1.8492",line(r,"MAT").totalConsumption());
        assertEquals(14450L,line(r,"HANGER").partNo()); eq("6",line(r,"HANGER").totalConsumption());
        eq("1433.22",r.total()); eq("716.61",products.preview(request,auth("ROLE_ADMIN")).total());
        assertTrue(r.lines().stream().allMatch(l -> l.calculation().productQuantity() == 3));
        verify(repository,never()).save(any(Parts.class)); verify(repository,never()).findById(28260L);
    }
    @Test void fractionalDimensionsAreRoundedAfterAddingMarginsAndBeforeLayerMultiplication() {
        var r = products.preview(request("100.1","200.1",2,27540L,3,4460L,null,5160L,2,
                new MatMargins(n(".2"),n(".3"),n(".4"),n(".5"))),auth("ROLE_LEVEL2"));
        eq(".121806",line(r,"GLASS").totalConsumption()); // 101 * 201 / 1e6 * 3 * 2
        eq(".040602",line(r,"BACKING").totalConsumption()); eq(".081204",line(r,"MAT").totalConsumption());
    }
    @Test void missingLayerCountsDefaultToOneAndNoMatKeepsOriginalDimensions() {
        var r = products.preview(request("400","600",2,27540L,null,4460L,null,null,null,null),auth("ROLE_LEVEL2"));
        eq(".48",line(r,"GLASS").totalConsumption()); eq(".48",line(r,"BACKING").totalConsumption());
        eq("4.32",line(r,"FRAME").totalConsumption());
    }
    @Test void pvcAndDvpHaveIdenticalSuspensionPlansAtEveryBoundary() {
        for (String width : List.of("149.999","150","299.999","300","400","400.001","450","450.001","800","800.001")) {
            for (Long mirror : Arrays.asList(null,27760L)) {
                var dvp = suspensions.preview(new SuspensionPreviewRequest(n(width),2,4460L,mirror,
                        SuspensionRules.Mode.AUTO,null,null,false,null),auth("ROLE_LEVEL2"));
                var pvc = suspensions.preview(new SuspensionPreviewRequest(n(width),2,27520L,mirror,
                        SuspensionRules.Mode.AUTO,null,null,false,null),auth("ROLE_LEVEL2"));
                assertEquals(dvp,pvc);
            }
        }
        var pozzi = suspensions.preview(new SuspensionPreviewRequest(n("300"),2,27520L,null,
                SuspensionRules.Mode.NONE,null,null,true,null),auth("ROLE_LEVEL2"));
        assertEquals(2,pozzi.selection().pozziPerProduct()); eq("4",pozzi.lines().get(0).calculation().totalConsumption());
    }
    @Test void mirrorsSupportPvcLayersWithoutMultiplyingGlueOrHangers() {
        var r = products.preview(new ProductPreviewRequest(ProductPreviewRequest.ProductType.MIRROR_IN_FRAME,
                n("400"),n("600"),2,29340L,27760L,null,27520L,
                new SuspensionSelection(SuspensionRules.Mode.AUTO,null,null,false,null),null,null,3,null,null,null),auth("ROLE_LEVEL2"));
        eq("1.44",line(r,"BACKING").totalConsumption()); eq("4",line(r,"MIRROR_GLUE").totalConsumption());
        eq("4",line(r,"HANGER").totalConsumption()); assertEquals(14460L,line(r,"HANGER").partNo());
    }
    @Test void invalidSelectionsAndOversizedOuterDimensionsAreRejected() {
        for (var r : List.of(
                request("400","600",1,null,2,null,null,null,null,null),
                request("400","600",1,null,null,null,2,null,null,null),
                request("400","600",1,null,null,null,null,null,2,null),
                request("400","600",1,null,null,null,null,null,null,margins()),
                request("400","600",1,null,null,null,null,5160L,1,null),
                request("99999","600",1,null,null,null,null,5160L,1,margins()),
                request("400","600",1,5230L,1,null,null,null,null,null)))
            assertThrows(PreviewException.class,() -> products.preview(r,auth("ROLE_LEVEL2")));
        verifyNoInteractions(repository);
    }
    @Test void wrongSheetMetadataCannotBeUsedOrSilentlyPricedAsZero() {
        when(catalog.get(5160L).getCalculationMethod()).thenReturn(CalculationMethod.PERIMETER);
        assertEquals("INVALID_COMPONENT_SETTINGS",assertThrows(PreviewException.class,() -> products.preview(
                request("400","600",1,null,null,null,null,5160L,1,margins()),auth("ROLE_LEVEL2"))).getCode());
        when(catalog.get(27540L).getListPrice()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED",assertThrows(PreviewException.class,() -> products.preview(
                request("400","600",1,27540L,1,null,null,null,null,null),auth("ROLE_LEVEL2"))).getCode());
    }
    private static final String JSON = """
            {"items":[{"clientItemId":"art-1","kind":"PRODUCT","product":{
              "productType":"FRAMED_ARTWORK","widthMm":430,"heightMm":600,"productQuantity":3,
              "framePartNo":29340,"glassPartNo":27540,"glassLayers":2,"backingPartNo":27520,"backingLayers":3,
              "matPartNo":5160,"matLayers":2,"matMargins":{"leftMm":10,"rightMm":20,"topMm":30,"bottomMm":40},
              "suspension":{"hangerMode":"AUTO","includePozzi":false}}}]}
            """;
    @Test void estimateHttpPricesNewCompositionAndReturnsProductQuantityUnchanged() throws Exception {
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1433.22))
                .andExpect(jsonPath("$.items[0].product.productQuantity").value(3))
                .andExpect(jsonPath("$.items[0].product.lines[1].calculation.consumptionPerProduct").value(.6164));
    }
    @Test void estimateHttpRejectsInvalidNestedFieldsAndFractionalCounts() throws Exception {
        for (String invalid : List.of(JSON.replace("\"glassLayers\":2","\"glassLayers\":0"),
                JSON.replace("\"backingLayers\":3","\"backingLayers\":10001"),
                JSON.replace("\"leftMm\":10","\"leftMm\":-1"),
                JSON.replace("\"leftMm\":10","\"leftMm\":null"),
                JSON.replace("\"leftMm\":10","\"leftMm\":0.0001"),
                JSON.replace("\"matLayers\":2","\"matLayers\":1.5")))
            mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void estimateErrorIdentifiesInvalidProduct() throws Exception {
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(JSON.replace("\"glassPartNo\":27540","\"glassPartNo\":999")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.clientItemId").value("art-1"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SHEET_PART"));
    }

    private static SheetSelection sheet(long partNo, int quantity) { return new SheetSelection(partNo, quantity); }
    private static SuspensionSelection auto() { return new SuspensionSelection(SuspensionRules.Mode.AUTO,null,null,false,null); }
    private ProductPreviewRequest grouped(String width, String height, int quantity, List<SheetSelection> glass,
            List<SheetSelection> backings, List<SheetSelection> mats, MatMargins margins, SuspensionSelection suspension) {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.FRAMED_ARTWORK,n(width),n(height),quantity,
                29340L,null,null,null,suspension,null,null,null,null,null,margins,glass,backings,mats);
    }
    private ProductPreviewRequest layeredArtwork(String bottom) {
        return grouped("400","600",2,List.of(sheet(12670,2),sheet(27510,1)),
                List.of(sheet(4460,1),sheet(27520,2)),List.of(sheet(5160,2),sheet(28010,1),sheet(29500,1)),
                new MatMargins(n("50"),n("50"),n("50"),n(bottom)),auto());
    }
    private MaterialPreviewResponse partLine(ProductPreviewResponse r, long partNo) {
        return r.lines().stream().map(ProductPreviewResponse.Line::calculation)
                .filter(l -> l.partNo() == partNo).findFirst().orElseThrow();
    }
    private static String example(String filename) throws Exception {
        return Files.readString(Path.of("examples","step11",filename));
    }
    @Test void multipleMaterialsInEveryGroupShareOneGeometryAndKeepAllQuantities() {
        var r = products.preview(layeredArtwork("70"),auth("ROLE_LEVEL2"));
        // 500 x 720, ten sheets/product, two products. Geometry does not accumulate per layer.
        eq("5.2",line(r,"FRAME").totalConsumption());
        eq("1.44",partLine(r,12670).totalConsumption()); eq(".72",partLine(r,27510).totalConsumption());
        eq(".72",partLine(r,4460).totalConsumption()); eq("1.44",partLine(r,27520).totalConsumption());
        eq("1.44",partLine(r,5160).totalConsumption()); eq(".72",partLine(r,28010).totalConsumption());
        eq(".72",partLine(r,29500).totalConsumption()); eq("4",line(r,"HANGER").totalConsumption());
        assertEquals(9,r.lines().size()); eq("1280",r.total());
        eq("1252",products.preview(layeredArtwork("50"),auth("ROLE_LEVEL2")).total());
        verify(repository,never()).save(any(Parts.class));
    }
    @Test void groupedCompositionUsesEveryPriceLevelAndAdminHasPriority() {
        var request = layeredArtwork("70");
        eq("640",products.preview(request,auth("ROLE_ADMIN","ROLE_COUNTER","ROLE_LEVEL2")).total());
        eq("320",products.preview(request,auth("ROLE_COUNTER","ROLE_LEVEL2")).total());
        eq("1280",products.preview(request,auth("ROLE_LEVEL2")).total());
        eq("2560",products.preview(request,auth("ROLE_USER")).total());
    }
    @Test void mirrorInsideGlassListAddsOneGluePerimeterAndOverridesPvcHangers() {
        var margins = new MatMargins(n("50"),n("50"),n("10"),n("20"));
        var suspension = new SuspensionSelection(SuspensionRules.Mode.AUTO,null,5,false,null);
        var r = products.preview(grouped("300","500",3,List.of(sheet(12670,1),sheet(27760,2)),
                List.of(sheet(27520,3)),List.of(sheet(5160,1)),margins,suspension),auth("ROLE_LEVEL2"));
        eq("1.272",line(r,"MIRROR").totalConsumption()); eq("5.58",line(r,"MIRROR_GLUE").totalConsumption());
        eq("15",line(r,"HANGER").totalConsumption()); assertEquals(14460L,line(r,"HANGER").partNo());
        eq("1.5",line(r,"CORD").totalConsumption());
        assertEquals(1,r.lines().stream().filter(l -> l.role().equals("MIRROR_GLUE")).count());
        var large = products.preview(grouped("300.001","500",3,List.of(sheet(27760,2)),
                List.of(sheet(4460,1),sheet(27520,3)),List.of(sheet(5160,1)),margins,auto()),auth("ROLE_LEVEL2"));
        assertEquals(29650L,line(large,"HANGER").partNo());
        assertTrue(large.lines().stream().noneMatch(l -> l.role().equals("CORD")));
        var crocodile = new SuspensionSelection(SuspensionRules.Mode.MANUAL,14450L,null,false,null);
        assertEquals("INVALID_SUSPENSION_SELECTION",assertThrows(PreviewException.class,() -> products.preview(
                grouped("300","500",1,List.of(sheet(27760,1)),List.of(sheet(27520,1)),null,null,crocodile),
                auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void rawOuterWidthControlsSuspensionThresholdsAndCordLength() {
        var margins = new MatMargins(n("50"),n("50"),n("0"),n("0"));
        for (long backing : new long[]{4460,27520}) {
            for (String[] c : new String[][]{{"49.999","27580","1"},{"50","14450","1"},
                    {"350","14450","1"},{"350.001","14450","2"}}) {
                var r = products.preview(grouped(c[0],"600",2,null,List.of(sheet(backing,4)),
                        List.of(sheet(5160,3)),margins,auto()),auth("ROLE_LEVEL2"));
                assertEquals(Long.valueOf(c[1]),line(r,"HANGER").partNo());
                eq(c[2],line(r,"HANGER").consumptionPerProduct());
            }
        }
        var r = products.preview(grouped("699.999","600",2,null,null,
                List.of(sheet(5160,3)),margins,new SuspensionSelection(SuspensionRules.Mode.AUTO,null,7,false,null)),auth("ROLE_LEVEL2"));
        assertEquals(14460L,line(r,"HANGER").partNo()); eq("1.799998",line(r,"CORD").totalConsumption());
        var large = products.preview(grouped("700.001","600",2,null,null,
                List.of(sheet(5160,3)),margins,auto()),auth("ROLE_LEVEL2"));
        assertEquals(29650L,line(large,"HANGER").partNo()); eq("1.800002",line(large,"CORD").totalConsumption());
    }
    @Test void pvcSupportsManualTypesAndPozziWithoutAddingExtraCord() {
        for (long hanger : new long[]{27580,14450,14460,29650}) {
            var selection = new SuspensionSelection(SuspensionRules.Mode.MANUAL,hanger,3,false,null);
            var r = products.preview(grouped("300","400",2,null,List.of(sheet(27520,3)),null,null,selection),auth("ROLE_LEVEL2"));
            eq("6",line(r,"HANGER").totalConsumption());
            assertEquals(hanger == 14460 || hanger == 29650, r.lines().stream().anyMatch(l -> l.role().equals("CORD")));
        }
        for (String width : List.of("299.999","300")) {
            var r = products.preview(grouped(width,"400",2,null,List.of(sheet(27520,3)),null,null,
                    new SuspensionSelection(SuspensionRules.Mode.NONE,null,null,true,null)),auth("ROLE_LEVEL2"));
            eq(width.equals("300") ? "4" : "2",line(r,"POZZI").totalConsumption());
            assertTrue(r.lines().stream().noneMatch(l -> l.role().equals("CORD") || l.role().equals("HANGER")));
        }
        var r = products.preview(grouped("300","400",2,null,List.of(sheet(27520,3)),null,null,
                new SuspensionSelection(SuspensionRules.Mode.AUTO,null,3,true,5)),auth("ROLE_LEVEL2"));
        eq("10",line(r,"POZZI").totalConsumption());
        for (Long hanger : Arrays.asList(14460L,29650L))
            assertThrows(PreviewException.class,() -> products.preview(grouped("300","400",2,null,
                    List.of(sheet(27520,3)),null,null,new SuspensionSelection(SuspensionRules.Mode.MANUAL,hanger,3,true,1)),auth("ROLE_LEVEL2")));
    }
    @Test void allConfirmedPartsAreAcceptedOnlyInTheirGroups() {
        for (long id : new long[]{12670,27540,27510,27470,27760})
            assertDoesNotThrow(() -> products.preview(grouped("100","200",1,List.of(sheet(id,1)),null,null,null,null),auth("ROLE_LEVEL2")));
        for (long id : new long[]{4460,27520})
            assertDoesNotThrow(() -> products.preview(grouped("100","200",1,null,List.of(sheet(id,1)),null,null,null),auth("ROLE_LEVEL2")));
        for (long id : new long[]{29500,28010,5160})
            assertDoesNotThrow(() -> products.preview(grouped("100","200",1,null,null,List.of(sheet(id,1)),margins(),null),auth("ROLE_LEVEL2")));
        for (long id : new long[]{5230,12680,4460,27520,5160,999})
            assertThrows(PreviewException.class,() -> products.preview(grouped("100","200",1,List.of(sheet(id,1)),null,null,null,null),auth("ROLE_LEVEL2")));
        assertThrows(PreviewException.class,() -> products.preview(grouped("100","200",1,null,List.of(sheet(27760,1)),null,null,null),auth("ROLE_LEVEL2")));
        assertThrows(PreviewException.class,() -> products.preview(grouped("100","200",1,null,null,List.of(sheet(27540,1)),margins(),null),auth("ROLE_LEVEL2")));
    }
    @Test void emptyListsAddNoComponentsAndDuplicateOrAmbiguousSelectionsFail() {
        assertEquals(1,products.preview(grouped("100","200",1,List.of(),List.of(),List.of(),null,null),auth("ROLE_LEVEL2")).lines().size());
        assertEquals("DUPLICATE_SHEET_PART",assertThrows(PreviewException.class,() -> products.preview(
                grouped("100","200",1,List.of(sheet(12670,1),sheet(12670,2)),null,null,null,null),auth("ROLE_LEVEL2"))).getCode());
        var ambiguous = new ProductPreviewRequest(ProductPreviewRequest.ProductType.FRAMED_ARTWORK,n("100"),n("200"),1,
                29340L,null,null,null,null,12670L,1,null,null,null,null,List.of(sheet(12670,1)),null,null);
        assertEquals("AMBIGUOUS_SHEET_SELECTION",assertThrows(PreviewException.class,() -> products.preview(ambiguous,auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void roundRawLayerAmountsOnceThenSumFinalIndependentProducts() {
        when(catalog.get(29340L).getListPrice()).thenReturn(0.0);
        when(catalog.get(12670L).getListPrice()).thenReturn(.001);
        when(catalog.get(27540L).getListPrice()).thenReturn(.001);
        var request = grouped("1000","1000",1,List.of(sheet(12670,2),sheet(27540,3)),null,null,null,null);
        var r = products.preview(request,auth("ROLE_LEVEL2"));
        eq("0",r.linesSubtotal()); eq(".01",r.roundingAdjustment()); eq(".01",r.total());
        var estimate = estimates.preview(new EstimatePreviewRequest(List.of(
                new EstimatePreviewRequest.Item("one",EstimatePreviewRequest.Kind.PRODUCT,request,null),
                new EstimatePreviewRequest.Item("two",EstimatePreviewRequest.Kind.PRODUCT,request,null))),auth("ROLE_LEVEL2"));
        eq(".02",estimate.total());
        when(catalog.get(12670L).getListPrice()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED",assertThrows(PreviewException.class,() -> products.preview(request,auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void publishedExamplesRunThroughHttpIncludingOldMirrorAndMounting() throws Exception {
        mvc.perform(post("/api/calculations/product-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(example("framed-artwork.json")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1280));
        mvc.perform(post("/api/calculations/product-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(example("mirror-layers.json")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1272))
                .andExpect(jsonPath("$.lines[6].calculation.partNo").value(14460));
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(example("estimate-mixed.json")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2979.82))
                .andExpect(jsonPath("$.items[0].total").value(1280))
                .andExpect(jsonPath("$.items[1].total").value(1272))
                .andExpect(jsonPath("$.items[2].total").value(309))
                .andExpect(jsonPath("$.items[3].total").value(118.82));
    }
    @Test void groupedHttpValidatesNestedCountsAndIgnoresSpoofedSuspensionContext() throws Exception {
        var json = example("framed-artwork.json");
        for (String invalid : List.of(json.replace("\"quantityPerProduct\": 2","\"quantityPerProduct\": 1.5"),
                json.replace("\"quantityPerProduct\": 2","\"quantityPerProduct\": 0"),
                json.replace("\"quantityPerProduct\": 2","\"quantityPerProduct\": null"),
                json.replace("\"quantityPerProduct\": 2","\"quantityPerProduct\": 10001"),
                json.replace("\"partNo\": 12670","\"partNo\": null"),
                json.replace("{ \"partNo\": 12670, \"quantityPerProduct\": 2 }","null")))
            mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                    .contentType("application/json").content("{\"items\":[{\"clientItemId\":\"a\",\"kind\":\"PRODUCT\",\"product\":" + invalid + "}]}"))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
        mvc.perform(post("/api/calculations/product-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content(example("mirror-layers.json").replace("\"hangerMode\": \"AUTO\"",
                        "\"hangerMode\": \"AUTO\", \"hasMirror\": false, \"mirrorPartNo\": null, \"widthMm\": 1, \"productQuantity\": 99")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[6].calculation.partNo").value(14460))
                .andExpect(jsonPath("$.lines[6].calculation.totalConsumption").value(15));
    }
    @Test void pvcWorksThroughExistingSuspensionHttpContract() throws Exception {
        mvc.perform(post("/api/calculations/suspension-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("""
                {"widthMm":300,"productQuantity":2,"backingPartNo":27520,"hangerMode":"AUTO","includePozzi":true}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selection.hangerPartNo").value(14450))
                .andExpect(jsonPath("$.selection.pozziPerProduct").value(2));
    }

    @Test void explicitCordChoiceWorksThroughProductComposition() {
        var noCord = new SuspensionSelection(SuspensionRules.Mode.AUTO, null, null, false, null, false);
        var r = products.preview(grouped("300", "400", 2, null, null, null, null, noCord), auth("ROLE_LEVEL2"));
        assertTrue(r.lines().stream().noneMatch(l -> l.role().equals("CORD")));
        eq("4", line(r, "HANGER").totalConsumption());
        verify(repository, never()).findById(28260L);
        var withCord = new SuspensionSelection(SuspensionRules.Mode.AUTO, null, null, false, null, true);
        var included = products.preview(grouped("300", "400", 2, null, null, null, null, withCord), auth("ROLE_LEVEL2"));
        eq(".8", line(included, "CORD").totalConsumption());
        assertEquals("INVALID_SUSPENSION_SELECTION", assertThrows(PreviewException.class, () -> products.preview(
                grouped("300", "400", 1, null, List.of(sheet(27520,1)), null, null, withCord),
                auth("ROLE_LEVEL2"))).getCode());
    }

    @Test void explicitCordExclusionIsDeserializedThroughEstimateHttp() throws Exception {
        String product = example("mirror-layers.json").replace("\"hangerMode\": \"AUTO\"",
                "\"hangerMode\": \"AUTO\", \"includeCord\": false");
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth("ROLE_LEVEL2"))
                .contentType("application/json").content("{\"items\":[{\"clientItemId\":\"cord\",\"kind\":\"PRODUCT\",\"product\":" + product + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].product.lines[?(@.role == 'CORD')]").isEmpty());
    }
}

