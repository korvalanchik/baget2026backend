package com.example.baget.warehouse;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WarehouseServiceTest {
    JdbcTemplate db; InventoryReservations reservations; WarehouseService service; UsersRepository users; Authentication auth;
    com.example.baget.orderproduct.ReworkService reworks=mock(com.example.baget.orderproduct.ReworkService.class);
    BigDecimal n(String s){return new BigDecimal(s);}
    @BeforeEach void setup(){
        db=mock(JdbcTemplate.class);reservations=mock(InventoryReservations.class);users=mock(UsersRepository.class);
        var user=new User();var role=new Role();role.setName("ROLE_ADMIN");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
        service=spy(new WarehouseService(db,users,reservations,new ObjectMapper(),reworks));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"paymentCheck",mock(com.example.baget.finance.ProductPaymentCheck.class));
        when(db.queryForMap("SELECT CAST(OnHand AS DECIMAL(24,6)) AS stock,unit_type AS unit FROM parts WHERE PartNo=? AND part_kind='MATERIAL' FOR UPDATE",1L)).thenReturn(Map.of("stock",n("10"),"unit","METER"));
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1",BigDecimal.class,1L)).thenReturn(n("6"));
        doReturn(Map.of()).when(service).material(1,auth);
    }
    WarehouseService.Stock request(String operation,String qty,String expected){return new WarehouseService.Stock("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",operation,n(qty),n(expected),"Документ 1");}
    @Test void nullSearchReturnsMaterialsInsteadOfFilteringEverythingOut() {
        Map<String,Object> row=Map.of("partNo",27540L,"onHand",n("5"));
        when(db.queryForList(contains("FROM parts p"),eq(""),eq(""),eq(0),eq(0)))
            .thenReturn(List.of(row));
        assertEquals(List.of(row),service.materials(null,0,false,auth));
        verify(db).queryForList(contains("FROM parts p"),eq(""),eq(""),eq(0),eq(0));
        verify(db,never()).update(anyString(),any(Object[].class));
        verifyNoInteractions(reservations);
    }
    @Test void blankSearchKeepsShortageFilterAndPagination() {
        service.materials("   ",1,true,auth);
        verify(db).queryForList(contains("FROM parts p"),eq(""),eq(""),eq(1),eq(100));
    }
    @Test void articleSearchIsTrimmed() {
        service.materials("  27540  ",0,false,auth);
        verify(db).queryForList(contains("FROM parts p"),eq("27540"),eq("27540"),eq(0),eq(0));
    }
    @Test void materialsListStillRequiresStockAccess() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        assertThrows(PreviewException.class,()->service.materials(null,0,false,auth));
        verifyNoInteractions(db,reservations);
    }
    @Test void countCannotDestroyReservations(){assertThrows(PreviewException.class,()->service.stock(1,request("COUNT","5","10"),auth));verifyNoInteractions(reservations);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void concurrentConsumptionMakesCountStale(){assertThrows(PreviewException.class,()->service.stock(1,request("COUNT","9","12"),auth));verifyNoInteractions(reservations);}
    @Test void receiptRefillsDemandAfterUpdatingPhysicalStock(){service.stock(1,request("RECEIPT","3","10"),auth);var order=inOrder(db,reservations);order.verify(db).update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",n("13"),1L);order.verify(reservations).refillPart(1,"admin");}
    @Test void successfulRetryNeverReceivesTwice(){
        var r=request("RECEIPT","3","10");
        when(db.queryForList("SELECT part_no,actor,request_json FROM material_stock_operations WHERE request_key=?",r.requestKey())).thenReturn(List.of(Map.of("part_no",1L,"actor","admin","request_json",new ObjectMapper().valueToTree(request("RECEIPT","3.000000","10.0")).toString())));
        service.stock(1,r,auth);verifyNoInteractions(reservations);verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void anotherRequestCannotReuseTheSameKey(){
        var r=request("RECEIPT","3","10");
        when(db.queryForList("SELECT part_no,actor,request_json FROM material_stock_operations WHERE request_key=?",r.requestKey())).thenReturn(List.of(Map.of("part_no",1L,"actor","admin","request_json",new ObjectMapper().valueToTree(request("RECEIPT","9","10")).toString())));
        assertThrows(PreviewException.class,()->service.stock(1,r,auth));verifyNoInteractions(reservations);
    }
    @Test void databaseRolesControlStockAccess(){when(users.findByUsername("admin")).thenReturn(Optional.empty());assertThrows(PreviewException.class,()->service.stock(1,request("COUNT","9","10"),auth));verifyNoInteractions(reservations);}
    WarehouseService.NewMaterial newMaterial(String description, UnitType unit, CalculationMethod method,
            java.math.BigDecimal profileWidthMm, java.math.BigDecimal marginWidthMm,
            java.math.BigDecimal marginHeightMm, java.math.BigDecimal allowanceMm,
            java.math.BigDecimal railWidthMm) {
        return new WarehouseService.NewMaterial(description, null, unit, method, profileWidthMm,
                marginWidthMm, marginHeightMm, allowanceMm, railWidthMm,
                n("2.5"), n("4.5"), n("4.0"), n("5.0"));
    }

    @Test void createMaterialWritesTypedCatalogRowAndReturnsGeneratedPartNo() {
        when(db.queryForList("SELECT PartNo FROM parts WHERE LOWER(TRIM(Description))=LOWER(TRIM(?)) LIMIT 1",
                Long.class, "PROFILE-NEW")).thenReturn(List.of());
        when(db.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(1);
            holder.getKeyList().add(Map.of("GENERATED_KEY", 9001L));
            return 1;
        });

        var result = service.createMaterial(newMaterial("PROFILE-NEW", UnitType.METER,
                CalculationMethod.FRAME_PROFILE, n("24"), null, null, null, null), auth);

        assertEquals(9001L, result.get("partNo"));
        assertEquals("PROFILE-NEW", result.get("description"));
        assertEquals("METER", result.get("unit"));
        assertEquals("FRAME_PROFILE", result.get("calculationMethod"));
        assertEquals(n("0"), result.get("onHand"));
        verify(db).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test void createMaterialRejectsDuplicateArticleBeforeInsert() {
        when(db.queryForList("SELECT PartNo FROM parts WHERE LOWER(TRIM(Description))=LOWER(TRIM(?)) LIMIT 1",
                Long.class, "DUPLICATE")).thenReturn(List.of(27540L));

        assertThrows(PreviewException.class, () -> service.createMaterial(newMaterial("DUPLICATE",
                UnitType.PIECE, CalculationMethod.QUANTITY, null, null, null, null, null), auth));
        verify(db, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test void createMaterialRequiresUnitCompatibleWithCalculationMethod() {
        when(db.queryForList("SELECT PartNo FROM parts WHERE LOWER(TRIM(Description))=LOWER(TRIM(?)) LIMIT 1",
                Long.class, "BAD-UNIT")).thenReturn(List.of());

        assertThrows(PreviewException.class, () -> service.createMaterial(newMaterial("BAD-UNIT",
                UnitType.PIECE, CalculationMethod.AREA, null, null, null, null, null), auth));
        verify(db, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test void createMaterialRequiresParametersForAreaWithMargins() {
        when(db.queryForList("SELECT PartNo FROM parts WHERE LOWER(TRIM(Description))=LOWER(TRIM(?)) LIMIT 1",
                Long.class, "NO-MARGIN")).thenReturn(List.of());

        assertThrows(PreviewException.class, () -> service.createMaterial(newMaterial("NO-MARGIN",
                UnitType.SQUARE_METER, CalculationMethod.AREA_WITH_MARGIN, null, null, null, null, null), auth));
        verify(db, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test void createMaterialRequiresWarehouseRole() {
        var user = new User();
        var role = new Role();
        role.setName("ROLE_MANAGER");
        user.setRoles(List.of(role));
        user.setAllowedBranches(Set.of());
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(PreviewException.class, () -> service.createMaterial(newMaterial("NO-ACCESS",
                UnitType.PIECE, CalculationMethod.QUANTITY, null, null, null, null, null), auth));
        verifyNoInteractions(db);
    }

    void trackingRow(long revision,String state,String location,String pending) {
        var p=new HashMap<String,Object>();p.put("id",10L);p.put("orderNo",1L);p.put("revision",revision);
        p.put("state",state);p.put("location",location);p.put("pending",pending);p.put("transferActor","admin");p.put("branchName","Test");
        when(db.queryForList(startsWith("SELECT p.id,p.order_no"),eq("token"))).thenAnswer(invocation->List.of(new HashMap<>(p)));
        doReturn(Map.of()).when(service).tracking("token",auth);
    }
    @Test void sendingDoesNotChangeCurrentLocation() {
        trackingRow(0,"COMPLETED","WORKSHOP",null);
        service.transfer("token",new WarehouseService.Transfer(0,"WAREHOUSE","Передача"),false,auth);
        verify(db).update("UPDATE order_products SET pending_location=?,transfer_actor=?,tracking_revision=tracking_revision+1 WHERE id=?","WAREHOUSE","admin",10L);
        verify(db,never()).update(startsWith("UPDATE order_products SET current_location="),any(Object[].class));
    }
    @Test void acceptingFinalCustomerProductMarksOrderAsReceived() {
        trackingRow(4,"COMPLETED","BRANCH","CUSTOMER");
        when(db.queryForObject(
            "SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND (COALESCE(current_location,'') <> 'CUSTOMER' OR pending_location IS NOT NULL)",
            Long.class, 1L)).thenReturn(0L);

        service.transfer("token", new WarehouseService.Transfer(4,"CUSTOMER",""), true, auth);

        verify(db).update(
            "UPDATE orders SET StatusOrder=12, ClientReceivedDate=COALESCE(ClientReceivedDate,CURRENT_TIMESTAMP(6)) WHERE OrderNo=?",
            1L);
    }

    @Test void acceptingCustomerProductDoesNotMarkOrderWhileOtherProductsRemain() {
        trackingRow(4,"COMPLETED","BRANCH","CUSTOMER");
        when(db.queryForObject(
            "SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND (COALESCE(current_location,'') <> 'CUSTOMER' OR pending_location IS NOT NULL)",
            Long.class, 1L)).thenReturn(1L);

        service.transfer("token", new WarehouseService.Transfer(4,"CUSTOMER",""), true, auth);

        verify(db, never()).update(startsWith("UPDATE orders SET StatusOrder=12"), any(Object[].class));
    }

    @Test void acceptingRequiresPendingDestination() {
        trackingRow(1,"COMPLETED","WORKSHOP","WAREHOUSE");
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(1,"CUSTOMER",""),true,auth));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void staleAcceptanceCannotCreateSecondMovement() {
        trackingRow(2,"COMPLETED","WAREHOUSE",null);
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(1,"WAREHOUSE",""),true,auth));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void unfinishedProductCannotBeTransferred() {
        trackingRow(0,"IN_PROGRESS","WORKSHOP",null);
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(0,"WAREHOUSE",""),false,auth));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void acceptingReturnInvalidatesEarlierWarehouseReceipt() {
        trackingRow(3,"COMPLETED","WAREHOUSE","WORKSHOP");
        service.transfer("token",new WarehouseService.Transfer(3,"WORKSHOP","Прийняв повернення"),true,auth);
        verify(db).update("UPDATE order_products SET warehouse_received_at=NULL WHERE id=?",10L);
        verify(db).update("UPDATE order_products SET current_location=?,pending_location=NULL,transfer_actor=NULL,tracking_revision=tracking_revision+1 WHERE id=?","WORKSHOP",10L);
    }
    @Test void returnRequiresReasonAndCannotBypassIssuedInvoice() {
        trackingRow(3,"COMPLETED","WAREHOUSE",null);
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(3,"WORKSHOP",""),false,auth));
        when(reworks.invoiced(1,10)).thenReturn(true);
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(3,"WORKSHOP","Дефект"),false,auth));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void branchScopeIsCheckedBeforeTakingOrderLock() {
        var user=new User();var role=new Role();role.setName("ROLE_FOREMAN");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        trackingRow(0,"COMPLETED","WORKSHOP",null);
        assertThrows(PreviewException.class,()->service.transfer("token",new WarehouseService.Transfer(0,"WAREHOUSE",""),false,auth));
        verify(db,never()).queryForObject("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,1L);
    }
}
