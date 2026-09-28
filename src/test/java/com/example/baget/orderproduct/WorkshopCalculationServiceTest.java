package com.example.baget.orderproduct;
import com.example.baget.calculation.*;
import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
import com.example.baget.parts.*;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WorkshopCalculationServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);CalculatedOrderService orders=mock(CalculatedOrderService.class);
    UsersRepository users=mock(UsersRepository.class);EstimatePreviewService preview=mock(EstimatePreviewService.class);
    PartsRepository parts=mock(PartsRepository.class);Validator validator=mock(Validator.class);ObjectMapper json=new ObjectMapper();
    Authentication auth=new UsernamePasswordAuthenticationToken("master","",List.of());
    WorkshopCalculationService service;Map<String,Object> product=new HashMap<>();
    static BigDecimal n(String s){return new BigDecimal(s);}
    ProductPreviewResponse.Line glass(String qty){return new ProductPreviewResponse.Line("GLASS","SELECTED",new MaterialPreviewResponse(27540L,"Скло",CalculationMethod.AREA,UnitType.SQUARE_METER,n(qty).divide(n("2")),2,n(qty),n("100"),n("24"),"UAH"));}
    EstimatePreviewResponse result(List<ProductPreviewResponse.Line> lines){return new EstimatePreviewResponse(List.of(new EstimatePreviewResponse.Item("old",EstimatePreviewRequest.Kind.PRODUCT,n("24"),new ProductPreviewResponse(ProductPreviewRequest.ProductType.UNFRAMED_ARTWORK,2,lines,n("24"),BigDecimal.ZERO,n("24"),"UAH"),null)),n("24"),"UAH");}
    WorkshopCalculationService.Request request(List<WorkshopCalculationService.Extra> extras){return new WorkshopCalculationService.Request(7,n("400"),n("500"),extras);}
    @BeforeEach void setup() throws Exception {
        var user=new User();var role=new Role();role.setName("ROLE_FOREMAN");user.setRoles(List.of(role));when(users.findByUsername("master")).thenReturn(Optional.of(user));
        when(validator.validate(any())).thenReturn(Set.of());
        when(orders.read(1,auth)).thenReturn(new CalculatedOrderService.View(1,7,2,"Філія",1,"Клієнт","","","",null,n("24"),List.of()));
        var component=json.readTree("{\"clientItemId\":\"old\",\"kind\":\"PRODUCT\",\"product\":{\"productType\":\"UNFRAMED_ARTWORK\",\"widthMm\":300,\"heightMm\":400,\"productQuantity\":2,\"glass\":[{\"partNo\":27540,\"quantityPerProduct\":1}]}}");
        product.put("configuration",json.writeValueAsString(Map.of("draft",Map.of("components",List.of(component)))));product.put("calculation",json.writeValueAsString(result(List.of(glass("0.24")))));product.put("production_state","IN_PROGRESS");product.put("production_confirmed",false);
        when(db.queryForList("SELECT configuration,calculation,production_state,production_confirmed,active_rework_id FROM order_products WHERE id=? AND order_no=? AND active=1",10L,1L)).thenReturn(List.of(product));
        when(db.queryForList(startsWith("SELECT ItemNo AS itemNo"),eq(1L),eq(10L))).thenReturn(List.of(Map.of("itemNo",1L,"partNo",27540L,"role","GLASS","unit","SQUARE_METER")));
        when(preview.preview(any(),any())).thenReturn(result(List.of(glass("0.4"))));
        var glass=new Parts();glass.setPartNo(27540L);glass.setUnitType(UnitType.SQUARE_METER);glass.setCalculationMethod(CalculationMethod.AREA);glass.setCalculationParams(Map.of());glass.setPartKind(PartKind.MATERIAL);when(parts.findById(27540L)).thenReturn(Optional.of(glass));
        service=new WorkshopCalculationService(db,orders,users,preview,new MaterialConsumptionService(),parts,json,validator,new PartPriceResolver());
    }
    @Test void reusesCompositionWithMeasuredDimensionsAndReturnsNoPrices() throws Exception {
        var r=service.calculate(1,10,request(null),auth);assertTrue(r.canApply());assertEquals(n("0.4"),r.lines().get(0).quantity());assertEquals(n("400"),r.lines().get(0).widthMm());assertEquals(2,r.productQuantity());
        var capture=org.mockito.ArgumentCaptor.forClass(EstimatePreviewRequest.class);verify(preview).preview(capture.capture(),any());assertEquals(n("400"),capture.getValue().items().get(0).product().widthMm());assertEquals(27540L,capture.getValue().items().get(0).product().glass().get(0).partNo());
        assertFalse(json.writeValueAsString(r).contains("unitPrice"));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void newCompositionPositionBlocksAutomaticReplacement(){var extra=new ProductPreviewResponse.Line("HANGER","AUTO",new MaterialPreviewResponse(27580L,"Підвіс",CalculationMethod.QUANTITY,UnitType.PIECE,BigDecimal.ONE,2,n("2"),BigDecimal.ONE,n("2"),"UAH"));var part=new Parts();part.setPartNo(27580L);part.setCalculationParams(Map.of());when(parts.findById(27580L)).thenReturn(Optional.of(part));when(preview.preview(any(),any())).thenReturn(result(List.of(glass("0.4"),extra)));var r=service.calculate(1,10,request(null),auth);assertFalse(r.canApply());assertEquals(2,r.lines().size());}
    @Test void emptyReworkSelectionDoesNotRecalculateAllItems(){product.put("active_rework_id",20L);var r=service.calculate(1,10,request(List.of()),auth);assertTrue(r.lines().isEmpty());}
    @Test void selectedOriginalReworkItemIsTheOnlyReturnedPosition(){product.put("active_rework_id",20L);when(db.queryForList("SELECT PartNo FROM items WHERE OrderNo=? AND ItemNo=? AND order_product_id=?",Long.class,1L,1L,10L)).thenReturn(List.of(27540L));var r=service.calculate(1,10,request(List.of(new WorkshopCalculationService.Extra(1L,27540,WorkshopCalculationService.Basis.OUTER,1))),auth);assertEquals(1,r.lines().size());assertEquals(n("0.4"),r.lines().get(0).quantity());}
    @Test void genericNewArticleUsesChosenMultiplicityAndProductCount(){product.put("active_rework_id",20L);var frame=new Parts();frame.setPartNo(888L);frame.setDescription("Багет");frame.setPartKind(PartKind.MATERIAL);frame.setUnitType(UnitType.METER);frame.setCalculationMethod(CalculationMethod.FRAME_PROFILE);frame.setCalculationParams(Map.of());frame.setProfilWidth(.04);when(parts.findById(888L)).thenReturn(Optional.of(frame));var r=service.calculate(1,10,request(List.of(new WorkshopCalculationService.Extra(null,888,WorkshopCalculationService.Basis.ARTWORK,2))),auth);assertEquals(0,n("8.48").compareTo(r.lines().get(0).quantity()));}
    @Test void changedRevisionAndCompletedProductCannotRecalculate(){assertThrows(PreviewException.class,()->service.calculate(1,10,new WorkshopCalculationService.Request(6,n("400"),n("500"),null),auth));product.put("production_confirmed",true);assertThrows(PreviewException.class,()->service.calculate(1,10,request(null),auth));verifyNoInteractions(preview);}
    @Test void sourceItemCannotBeSpoofed(){product.put("active_rework_id",20L);when(db.queryForList("SELECT PartNo FROM items WHERE OrderNo=? AND ItemNo=? AND order_product_id=?",Long.class,1L,1L,10L)).thenReturn(List.of(27540L));assertThrows(PreviewException.class,()->service.calculate(1,10,request(List.of(new WorkshopCalculationService.Extra(1L,999,WorkshopCalculationService.Basis.OUTER,1))),auth));}
}
