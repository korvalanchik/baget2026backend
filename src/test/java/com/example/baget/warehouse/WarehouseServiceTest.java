package com.example.baget.warehouse;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.orderproduct.InventoryReservations;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WarehouseServiceTest {
    JdbcTemplate db; InventoryReservations reservations; WarehouseService service; UsersRepository users; Authentication auth;
    com.example.baget.orderproduct.ReworkService reworks=mock(com.example.baget.orderproduct.ReworkService.class);
    BigDecimal n(String s){return new BigDecimal(s);}
    @BeforeEach void setup(){
        db=mock(JdbcTemplate.class);reservations=mock(InventoryReservations.class);users=mock(UsersRepository.class);
        var user=new User();var role=new Role();role.setName("ROLE_ADMIN");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
        service=spy(new WarehouseService(db,users,reservations,new ObjectMapper(),reworks));
        when(db.queryForMap("SELECT CAST(OnHand AS DECIMAL(24,6)) AS stock,unit_type AS unit FROM parts WHERE PartNo=? AND part_kind='MATERIAL' FOR UPDATE",1L)).thenReturn(Map.of("stock",n("10"),"unit","METER"));
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1",BigDecimal.class,1L)).thenReturn(n("6"));
        doReturn(Map.of()).when(service).material(1,auth);
    }
    WarehouseService.Stock request(String operation,String qty,String expected){return new WarehouseService.Stock("c30b2f11-8dbf-47bd-91f4-317f40ad1af2",operation,n(qty),n(expected),"Документ 1");}
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
