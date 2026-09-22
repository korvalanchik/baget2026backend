package com.example.baget.calculation.estimate;

import com.example.baget.calculation.*;
import com.example.baget.calculation.mounting.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
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

/** Real calculation/composition/pricing services; only repository and Parts are mocked. */
class EstimateCompositionTest {
    private ValidatorFactory factory;
    private PartsRepository repository;
    private EstimatePreviewService service;
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private void part(long id, CalculationMethod method, UnitType unit, PartKind kind,
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
    }
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        repository = mock(PartsRepository.class);
        part(29720, CalculationMethod.PERIMETER, UnitType.METER, PartKind.MATERIAL,
                Map.of("railWidthMm", n("46")), 82.5, 32);
        part(5130, CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER, PartKind.MATERIAL,
                Map.of("marginWidthMm", n("10"), "marginHeightMm", n("10")), 187.5, 50);
        part(33370, CalculationMethod.FIXED, UnitType.PIECE, PartKind.WORK, Map.of(), 46.2, 0);
        part(5140, CalculationMethod.PERIMETER, UnitType.METER, PartKind.WORK, Map.of(), 20, .06);
        part(27970, CalculationMethod.AREA, UnitType.SQUARE_METER, PartKind.MATERIAL, Map.of(), 602.5, 335);
        var prices = new PartPriceResolver();
        var validator = factory.getValidator();
        var materials = new MaterialPreviewService(repository, new MaterialConsumptionService(), prices, validator);
        var underframes = new UnderframePreviewComposer(repository, materials, prices, new UnderframeCrossbarService(28030));
        var products = new ProductPreviewService(new MirrorFrameRule(27760, 28540), repository,
                materials, prices, validator, underframes, mock(MirrorAccessoriesComposer.class));
        var mountings = new MountingPreviewService(repository, materials, prices, underframes, validator);
        service = new EstimatePreviewService(products, mountings, prices, validator);
    }
    @AfterEach void close() { factory.close(); }

    private EstimatePreviewRequest request() {
        var print = new MountingPreviewRequest(MountingPreviewRequest.MountingType.UNDERFRAME,
                n("600"), n("800"), 1, MountingPreviewRequest.CanvasType.PRINT, 27970L, 29720L);
        var plane = new MountingPreviewRequest(MountingPreviewRequest.MountingType.PLANE,
                n("600"), n("800"), 1, null, null, null);
        var frames = new ProductPreviewRequest(ProductPreviewRequest.ProductType.UNDERFRAME,
                n("600"), n("800"), 2, null, null, 29720L);
        return new EstimatePreviewRequest(List.of(
                new EstimatePreviewRequest.Item("print", EstimatePreviewRequest.Kind.MOUNTING, null, print),
                new EstimatePreviewRequest.Item("plane", EstimatePreviewRequest.Kind.MOUNTING, null, plane),
                new EstimatePreviewRequest.Item("separate-frames", EstimatePreviewRequest.Kind.PRODUCT, frames, null)));
    }
    private void check(String expected, String... roles) {
        var auth = new UsernamePasswordAuthenticationToken("test", "unused",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        var r = service.preview(request(), auth);
        assertEquals(n(expected), r.total());
        assertEquals(r.total(), r.items().stream().map(EstimatePreviewResponse.Item::total)
                .reduce(new BigDecimal("0.00"), BigDecimal::add));
        assertEquals(3, r.items().get(0).mounting().lines().size());
        assertEquals(2, r.items().get(1).mounting().lines().size());
        assertEquals(1, r.items().get(2).product().lines().size());
        verify(repository, never()).save(any(Parts.class));
        verify(repository, never()).findById(28030L);
    }
    @Test void level2UsesRealRulesAndAddsSeparateProductsOnce() { check("1267.42", "ROLE_LEVEL2"); }
    @Test void adminCostWinsForEveryProductInMixedEstimate() { check("504.73", "ROLE_ADMIN", "ROLE_LEVEL2"); }
}
