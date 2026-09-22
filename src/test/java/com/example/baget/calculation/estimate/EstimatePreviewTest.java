package com.example.baget.calculation.estimate;

import com.example.baget.calculation.mounting.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
import com.example.baget.config.JacksonConfig;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EstimatePreviewTest {
    private ValidatorFactory factory;
    private ProductPreviewService products;
    private MountingPreviewService mountings;
    private EstimatePreviewService service;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "test", "unused", List.of(new SimpleGrantedAuthority("ROLE_LEVEL2")));

    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) { assertEquals(0, n(expected).compareTo(actual)); }
    private ProductPreviewRequest product() {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n("600"), n("800"), 2, null, null, 29720L);
    }
    private MountingPreviewRequest mounting() {
        return new MountingPreviewRequest(MountingPreviewRequest.MountingType.PLANE,
                n("600"), n("800"), 1, null, null, null);
    }
    private EstimatePreviewRequest.Item productItem(String id) {
        return new EstimatePreviewRequest.Item(id, EstimatePreviewRequest.Kind.PRODUCT, product(), null);
    }
    private EstimatePreviewRequest.Item mountingItem(String id) {
        return new EstimatePreviewRequest.Item(id, EstimatePreviewRequest.Kind.MOUNTING, null, mounting());
    }
    private ProductPreviewResponse productResult(String total) {
        return new ProductPreviewResponse(ProductPreviewRequest.ProductType.UNDERFRAME, 2, List.of(),
                n(total), n("0.00"), n(total), "UAH");
    }
    private MountingPreviewResponse mountingResult(String total) {
        return new MountingPreviewResponse(MountingPreviewRequest.MountingType.PLANE, null, 1, List.of(),
                n(total), n("0.00"), n(total), "UAH");
    }

    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        products = mock(ProductPreviewService.class);
        mountings = mock(MountingPreviewService.class);
        service = new EstimatePreviewService(products, mountings, new PartPriceResolver(), factory.getValidator());
        when(products.preview(any(), any())).thenReturn(productResult("462.00"));
        when(mountings.preview(any(), any())).thenReturn(mountingResult("138.84"));
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new EstimatePreviewController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void close() { factory.close(); }

    @Test void routesEachItemOncePreservesOrderAndDoesNotMultiplyQuantityAgain() {
        var r = service.preview(new EstimatePreviewRequest(List.of(mountingItem("plane"), productItem("frame"))), auth);
        eq("600.84", r.total());
        assertEquals(List.of("plane", "frame"), r.items().stream().map(EstimatePreviewResponse.Item::clientItemId).toList());
        assertNull(r.items().get(0).product());
        assertNotNull(r.items().get(0).mounting());
        assertNotNull(r.items().get(1).product());
        assertNull(r.items().get(1).mounting());
        verify(products).preview(product(), auth);
        verify(mountings).preview(mounting(), auth);
    }
    @Test void sumsRoundedProductTotalsInsteadOfRoundingAllRawMaterialsAgain() {
        when(products.preview(any(), any())).thenReturn(productResult("0.01"));
        when(mountings.preview(any(), any())).thenReturn(mountingResult("0.01"));
        eq("0.02", service.preview(new EstimatePreviewRequest(List.of(productItem("a"), mountingItem("b"))), auth).total());
    }
    @Test void duplicateIdsFailBeforeCalculation() {
        var ex = assertThrows(EstimateItemException.class, () -> service.preview(
                new EstimatePreviewRequest(List.of(productItem("same"), mountingItem("same"))), auth));
        assertEquals("DUPLICATE_CLIENT_ITEM_ID", ex.getCode());
        assertEquals("same", ex.getClientItemId());
        verifyNoInteractions(products, mountings);
    }
    @Test void bothOrWrongPayloadsAreRejectedBeforeCalculation() {
        for (var item : List.of(
                new EstimatePreviewRequest.Item("bad", EstimatePreviewRequest.Kind.PRODUCT, product(), mounting()),
                new EstimatePreviewRequest.Item("bad", EstimatePreviewRequest.Kind.PRODUCT, null, mounting()),
                new EstimatePreviewRequest.Item("bad", EstimatePreviewRequest.Kind.MOUNTING, null, null))) {
            assertEquals("INVALID_ITEM_SELECTION", assertThrows(EstimateItemException.class,
                    () -> service.preview(new EstimatePreviewRequest(List.of(productItem("ok"), item)), auth)).getCode());
        }
        verifyNoInteractions(products, mountings);
    }
    @Test void nullEmptyOversizedAndNullItemsAreRejected() {
        var tooMany = java.util.stream.IntStream.range(0, 51).mapToObj(i -> productItem("id" + i)).toList();
        for (var r : Arrays.asList(null, new EstimatePreviewRequest(null), new EstimatePreviewRequest(List.of()),
                new EstimatePreviewRequest(tooMany), new EstimatePreviewRequest(Arrays.asList((EstimatePreviewRequest.Item) null)))) {
            assertEquals("INVALID_REQUEST", assertThrows(PreviewException.class, () -> service.preview(r, auth)).getCode());
        }
        verifyNoInteractions(products, mountings);
    }
    @Test void nestedValidationRunsForDirectServiceCalls() {
        var invalid = new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n("0"), n("800"), 1, null, null, 29720L);
        var item = new EstimatePreviewRequest.Item("bad", EstimatePreviewRequest.Kind.PRODUCT, invalid, null);
        assertEquals("INVALID_REQUEST", assertThrows(PreviewException.class,
                () -> service.preview(new EstimatePreviewRequest(List.of(item)), auth)).getCode());
        verifyNoInteractions(products, mountings);
    }
    @Test void failureRetainsStatusCodeAndIdentifiesItemWithoutContinuing() {
        when(mountings.preview(any(), any())).thenThrow(new PreviewException(
                HttpStatus.UNPROCESSABLE_ENTITY, "COMPOSITION_PART_MISSING", "Позицію не знайдено"));
        var ex = assertThrows(EstimateItemException.class, () -> service.preview(new EstimatePreviewRequest(
                List.of(productItem("first"), mountingItem("broken"), productItem("third"))), auth));
        assertEquals("broken", ex.getClientItemId());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("COMPOSITION_PART_MISSING", ex.getCode());
        verify(products, times(1)).preview(any(), same(auth));
    }
    @Test void unauthenticatedRequestDoesNotReachCalculators() {
        assertThrows(PreviewException.class, () -> service.preview(new EstimatePreviewRequest(List.of(productItem("a"))), null));
        verifyNoInteractions(products, mountings);
    }
    @Test void fiftyItemsAreAllowedAndHaveIndependentIds() {
        var items = java.util.stream.IntStream.range(0, 50).mapToObj(i -> productItem("id" + i)).toList();
        eq("23100.00", service.preview(new EstimatePreviewRequest(items), auth).total());
        verify(products, times(50)).preview(any(), same(auth));
    }
    @Test void httpUsesBackendResultAndIgnoresSubmittedPrice() throws Exception {
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth).contentType("application/json").content("""
                {"total":0,"items":[{"clientItemId":"a","kind":"PRODUCT","total":0,"product":{
                  "productType":"UNDERFRAME","widthMm":600,"heightMm":800,"productQuantity":2,
                  "underframePartNo":29720,"unitPrice":0}}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(462.00))
                .andExpect(jsonPath("$.items[0].clientItemId").value("a"));
    }
    @Test void httpNestedValidationReportsFieldPath() throws Exception {
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth).contentType("application/json").content("""
                {"items":[{"clientItemId":"a","kind":"PRODUCT","product":{
                  "productType":"UNDERFRAME","widthMm":0,"heightMm":800,"productQuantity":1,
                  "underframePartNo":29720}}]}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].product.widthMm"));
    }
    @Test void httpCompositionErrorReturnsItemIdAndNoPartialItems() throws Exception {
        when(mountings.preview(any(), any())).thenThrow(new PreviewException(
                HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MOUNTING_SETTINGS", "Налаштування відсутні"));
        mvc.perform(post("/api/calculations/estimate-preview").principal(auth).contentType("application/json").content("""
                {"items":[{"clientItemId":"plane","kind":"MOUNTING","mounting":{
                  "mountingType":"PLANE","widthMm":600,"heightMm":800,"productQuantity":1}}]}
                """))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.clientItemId").value("plane"))
                .andExpect(jsonPath("$.code").value("INVALID_MOUNTING_SETTINGS"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }
    @Test void httpUnknownKindAndFractionalProductQuantityAreRejected() throws Exception {
        for (String content : List.of("""
                {"items":[{"clientItemId":"a","kind":"OTHER"}]}
                """, """
                {"items":[{"clientItemId":"a","kind":"PRODUCT","product":{
                  "productType":"UNDERFRAME","widthMm":600,"heightMm":800,"productQuantity":1.5,
                  "underframePartNo":29720}}]}
                """)) {
            mvc.perform(post("/api/calculations/estimate-preview").principal(auth).contentType("application/json").content(content))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        }
    }
}
