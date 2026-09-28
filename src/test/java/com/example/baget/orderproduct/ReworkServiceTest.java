package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PartPriceResolver;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.parts.PartsRepository;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReworkServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    CalculatedOrderService orders=mock(CalculatedOrderService.class);
    ProductionService production=mock(ProductionService.class);
    InventoryReservations reservations=mock(InventoryReservations.class);
    UsersRepository users=mock(UsersRepository.class);
    PartsRepository parts=mock(PartsRepository.class);
    Validator validator=mock(Validator.class);
    ObjectMapper json=new ObjectMapper();
    ReworkService service;
    Authentication auth=new UsernamePasswordAuthenticationToken("master","",List.of());
    Map<String,Object> product=new HashMap<>(),cycle=new HashMap<>();
    static BigDecimal n(String s){return new BigDecimal(s);}
    @BeforeEach void setup() throws Exception {
        var user=new User();var role=new Role();role.setName("ROLE_FOREMAN");user.setRoles(List.of(role));
        when(users.findByUsername("master")).thenReturn(Optional.of(user));
        when(validator.validate(any())).thenReturn(Set.of());
        service=new ReworkService(db,orders,production,reservations,users,parts,new PartPriceResolver(),json,validator);
        when(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,1L)).thenReturn(List.of(1L));
        when(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE product_id=?",Long.class,10L)).thenReturn(0L);
        when(db.queryForObject("SELECT COUNT(*) FROM product_invoice_claims WHERE product_id=?",Long.class,10L)).thenReturn(0L);
        when(db.queryForObject("SELECT COUNT(*) FROM invoice_orders io WHERE io.order_no=? AND NOT EXISTS(SELECT 1 FROM invoice_products ip WHERE ip.invoice_id=io.invoice_id)",Long.class,1L)).thenReturn(0L);
        when(db.queryForObject("SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=0",Long.class,1L)).thenReturn(0L);
        when(orders.read(1,auth)).thenReturn(new CalculatedOrderService.View(1,7,3,"Філія",5,"Клієнт","","","",null,n("100"),List.of()));
        product.putAll(Map.of("id",10L,"production_revision",3L,"production_confirmed",true,"production_state","COMPLETED","current_location","WORKSHOP","confirmed_revision",3L,"actual_total",n("100"),"configuration","{}"));
        product.put("confirmed_calculation","{\"productId\":10,\"revision\":3,\"total\":100,\"lines\":[]}");
        product.put("measured_width_mm",n("300"));product.put("measured_height_mm",n("400"));
        when(db.queryForList("SELECT * FROM order_products WHERE id=? AND order_no=? AND active=1 FOR UPDATE",10L,1L)).thenReturn(List.of(product));
        when(db.queryForObject("SELECT COALESCE(SUM(actual_total),0) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=1",BigDecimal.class,1L)).thenReturn(n("100"));
        when(db.queryForObject("SELECT id FROM product_reworks WHERE product_id=? AND opened_revision=?",Long.class,10L,4L)).thenReturn(20L);
    }
    @Test void openingArchivesSnapshotAndDoesNotTouchStockOrOriginalItems() {
        service.open(1,10,new ReworkService.Open(7,ReworkRules.ChargeMode.WORKSHOP,"Дефект"),auth);
        verifyNoInteractions(reservations);
        verify(db).update(startsWith("INSERT INTO product_reworks("),eq(10L),eq(4L),eq("WORKSHOP"),eq("Дефект"),eq("master"),contains("\"total\":100"),eq(n("100")),eq(n("300")),eq(n("400")));
        assertFalse(mutatingSql().stream().anyMatch(s->s.startsWith("UPDATE parts ") || s.startsWith("UPDATE items ") || s.startsWith("INSERT INTO inventory_movements")));
    }
    @Test void invoiceAndWrongLocationRejectReopeningBeforeWrites() {
        product.put("current_location","WAREHOUSE");
        assertThrows(PreviewException.class,()->service.open(1,10,new ReworkService.Open(7,ReworkRules.ChargeMode.WORKSHOP,"Дефект"),auth));
        product.put("current_location","WORKSHOP");
        when(db.queryForObject("SELECT COUNT(*) FROM invoice_products WHERE product_id=?",Long.class,10L)).thenReturn(1L);
        assertThrows(PreviewException.class,()->service.open(1,10,new ReworkService.Open(7,ReworkRules.ChargeMode.WORKSHOP,"Дефект"),auth));
        assertTrue(mutatingSql().isEmpty());
    }
    @Test void staleRevisionAndBranchDenialCannotWrite() {
        assertThrows(PreviewException.class,()->service.open(1,10,new ReworkService.Open(6,ReworkRules.ChargeMode.WORKSHOP,"Зміна"),auth));
        when(orders.read(1,auth)).thenThrow(new PreviewException(org.springframework.http.HttpStatus.FORBIDDEN,"BRANCH","Філія"));
        assertThrows(PreviewException.class,()->service.open(1,10,new ReworkService.Open(7,ReworkRules.ChargeMode.WORKSHOP,"Зміна"),auth));
        assertTrue(mutatingSql().isEmpty());
    }
    void active(String mode) {
        product.put("production_confirmed",false);product.put("production_state","IN_PROGRESS");product.put("active_rework_id",20L);
        cycle.putAll(Map.of("id",20L,"saved",true,"charge_mode",mode,"baseline_total",n("100"),"width_mm",n("310"),"height_mm",n("410"),"work_note","Перероблено","reason","Дефект"));
        when(db.queryForMap("SELECT * FROM product_reworks WHERE id=? AND product_id=? AND state='OPEN' FOR UPDATE",20L,10L)).thenReturn(cycle);
        var line=new HashMap<String,Object>();line.putAll(Map.of("lineNo",1,"sourceItemNo",1L,"partNo",100L,"description","Багет","unit","METER","material",true,"quantity",n("2"),"price",n("10"),"cost",n("6")));
        when(db.queryForList(startsWith("SELECT line_no AS lineNo"),eq(20L))).thenReturn(List.of(line));
        when(db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,100L)).thenReturn(n("10"));
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,100L,10L)).thenReturn(n("8"));
        when(db.queryForObject("SELECT COALESCE(MAX(ItemNo),0)+1 FROM items WHERE OrderNo=?",Long.class,1L)).thenReturn(4L);
        when(production.view(1,auth)).thenReturn(new ProductionService.View(1,7,2,false,null,null,List.of()));
    }
    @Test void completionConsumesOnlyAdditionalQuantityAndAppendsItem() {
        active("WORKSHOP");service.complete(1,10,7,auth);
        verify(db).update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",n("8"),100L);
        verify(db).update("INSERT INTO inventory_movements(order_no,item_no,part_no,quantity_delta,stock_before,stock_after,actor) VALUES(?,?,?,?,?,?,?)",1L,4L,100L,n("-2"),n("10"),n("8"),"master");
        verify(reservations).releaseProduct(10,"master");
        assertTrue(mutatingSql().stream().anyMatch(s->s.startsWith("INSERT INTO items(")));
        assertFalse(mutatingSql().stream().anyMatch(s->s.startsWith("UPDATE items ")));
        verify(db).update(startsWith("UPDATE order_products SET active_rework_id=NULL"),eq(n("100.00")),eq(4L),contains("\"additionalCharge\":0.00"),eq(10L));
    }
    @Test void customerChangeAddsOnlyIncrementalCharge() {
        active("CUSTOMER");service.complete(1,10,7,auth);
        verify(db).update(startsWith("UPDATE order_products SET active_rework_id=NULL"),eq(n("120.00")),eq(4L),contains("\"additionalCharge\":20.00"),eq(10L));
    }
    @Test void shortageProtectsSiblingReservationsAndPostsNothing() {
        active("WORKSHOP");
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,100L,10L)).thenReturn(n("9"));
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));
        verify(reservations,never()).releaseProduct(anyLong(),anyString());assertTrue(mutatingSql().isEmpty());
    }
    @Test void completionNeedsExplicitSaveAndCannotRepeatClosedCycle() {
        active("WORKSHOP");cycle.put("saved",false);
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));
        cycle.put("saved",true);product.put("active_rework_id",null);
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));assertTrue(mutatingSql().isEmpty());
    }
    @Test void emptyExtrasSaveReleasesDemandWithoutReturningPhysicalStock() {
        active("WORKSHOP");
        when(db.queryForList("SELECT part_no FROM material_reservations WHERE order_product_id=? AND active=1",Long.class,10L)).thenReturn(List.of(100L));
        service.save(1,10,new ReworkService.Save(7,n("300"),n("400"),"Лише обмір",List.of()),auth);
        verify(reservations).reworkDemand(10,100,BigDecimal.ZERO,"master");
        verify(reservations).refillPart(100,"master");
        assertFalse(mutatingSql().stream().anyMatch(s->s.startsWith("UPDATE parts ") || s.startsWith("INSERT INTO inventory_movements")));
    }
    private List<String> mutatingSql() {
        return mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("update")).map(i->(String)i.getArgument(0)).toList();
    }
    @Test void newCustomerChangeRequiresNewOrderAndCannotWrite() {
        assertThrows(com.example.baget.calculation.preview.PreviewException.class,
            () -> service.open(1,10,new ReworkService.Open(3,ReworkRules.ChargeMode.CUSTOMER,"Заміна багету"),auth));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
