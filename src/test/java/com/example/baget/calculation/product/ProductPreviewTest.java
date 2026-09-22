package com.example.baget.calculation.product;

import com.example.baget.calculation.MaterialConsumptionService;
import com.example.baget.calculation.preview.*;
import com.example.baget.parts.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPreviewTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private ProductPreviewService service;
    private Parts frame;
    private Parts mirror;
    private Parts glue;
    private final MirrorFrameRule rule = new MirrorFrameRule(27760, 28540);
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual));
    }
    private ProductPreviewRequest request(int qty) {
        return new ProductPreviewRequest(ProductPreviewRequest.ProductType.MIRROR_IN_FRAME,
                n("600"), n("800"), qty, 29340L, 27760L);
    }
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken("test", "unused", List.of(new SimpleGrantedAuthority(role)));
    }
    private Parts material(long id, CalculationMethod method, UnitType unit, double price, double cost) {
        var p = mock(Parts.class);
        when(p.getPartNo()).thenReturn(id);
        when(p.getDescription()).thenReturn("test " + id);
        when(p.getCalculationMethod()).thenReturn(method);
        when(p.getUnitType()).thenReturn(unit);
        when(p.getPartKind()).thenReturn(PartKind.MATERIAL);
        when(p.getCalculationParams()).thenReturn(Map.of());
        when(p.getListPrice()).thenReturn(price);
        when(p.getCost()).thenReturn(cost);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        frame = material(29340, CalculationMethod.FRAME_PROFILE, UnitType.METER, 102.5, 25);
        when(frame.getProfilWidth()).thenReturn(0.024);
        mirror = material(27760, CalculationMethod.AREA, UnitType.SQUARE_METER, 1297.5, 150);
        glue = material(28540, CalculationMethod.PERIMETER, UnitType.METER, 20, 10);
        var prices = new PartPriceResolver();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, factory.getValidator());
        service = new ProductPreviewService(rule, repository, materials, prices, factory.getValidator(),
                mock(UnderframePreviewComposer.class), mock(MirrorAccessoriesComposer.class));
    }
    @AfterEach void close() { factory.close(); }

    @Test void automaticallyAddsExactlyOneGlueLineAndMultipliesOnce() {
        var result = service.preview(request(2), auth("ROLE_LEVEL2"));
        assertEquals(3, result.lines().size());
        var glueLine = result.lines().get(2);
        assertEquals("MIRROR_IN_FRAME_RULE", glueLine.source());
        assertEquals(28540L, glueLine.calculation().partNo());
        eq("5.6", glueLine.calculation().totalConsumption());
        eq("1970.96", result.total());
        eq("0", result.roundingAdjustment());
        verify(repository, never()).save(any(Parts.class));
        verify(repository, never()).delete(any(Parts.class));
    }
    @Test void adminUsesCostForAllComponents() {
        eq("349.60", service.preview(request(2), auth("ROLE_ADMIN")).total());
    }
    @Test void repeatedPreviewDoesNotAccumulateAutomaticLines() {
        assertEquals(3, service.preview(request(1), auth("ROLE_LEVEL2")).lines().size());
        assertEquals(3, service.preview(request(1), auth("ROLE_LEVEL2")).lines().size());
    }
    @Test void rejectsArbitraryAreaPartAsMirror() {
        var r = new ProductPreviewRequest(ProductPreviewRequest.ProductType.MIRROR_IN_FRAME,
                n("600"), n("800"), 1, 29340L, 29500L);
        assertEquals("INVALID_COMPOSITION", assertThrows(PreviewException.class,
                () -> service.preview(r, auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void rejectsNonFrameMaterialInFrameSlot() {
        when(frame.getCalculationMethod()).thenReturn(CalculationMethod.PERIMETER);
        assertEquals("INVALID_COMPONENT_SETTINGS", assertThrows(PreviewException.class,
                () -> service.preview(request(1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void missingAutomaticComponentFailsWholePreview() {
        when(repository.findById(28540L)).thenReturn(Optional.empty());
        assertEquals("COMPOSITION_PART_MISSING", assertThrows(PreviewException.class,
                () -> service.preview(request(1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void missingGluePriceDoesNotReturnPartialTotal() {
        when(glue.getListPrice()).thenReturn(null);
        assertEquals("PRICE_NOT_CONFIGURED", assertThrows(PreviewException.class,
                () -> service.preview(request(1), auth("ROLE_LEVEL2"))).getCode());
    }
    @Test void validatesInputBeforeRepositoryAccess() {
        assertThrows(PreviewException.class, () -> service.preview(request(0), auth("ROLE_LEVEL2")));
        verifyNoInteractions(repository);
    }
    @Test void unauthenticatedRequestDoesNotReadParts() {
        assertThrows(PreviewException.class, () -> service.preview(request(1), null));
        verifyNoInteractions(repository);
    }
    @Test void roundsAfterSummingAndExposesDifference() {
        var total = ProductTotals.fromUnroundedAmounts(List.of(n("0.335"), n("0.335"), n("0.335")));
        eq("1.02", total.linesSubtotal());
        eq("-0.01", total.roundingAdjustment());
        eq("1.01", total.total());
        assertEquals(total.total(), total.linesSubtotal().add(total.roundingAdjustment()));
    }
    @Test void conflictingRuleIdsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new MirrorFrameRule(27760, 27760));
    }
}
