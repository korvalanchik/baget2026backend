package com.example.baget.orderproduct;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static com.example.baget.orderproduct.WorkshopRules.State.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkshopServiceTest {
    JdbcTemplate db;
    InventoryReservations reservations;
    WorkshopService service;
    Authentication auth;
    UsersRepository users;
    static BigDecimal n(String s) { return new BigDecimal(s); }
    WorkshopService.Product product(boolean confirmed) {
        var line=new ProductionService.Line(1,10,"art-1",100,"Матеріал","FRAME","METER",n("5"),n("5"),null,null,n("10"),n("50"),true);
        return new WorkshopService.Product(10,"art-1",IN_PROGRESS,2,n("300"),n("400"),"Обмір","master",null,null,
            confirmed,null,null,null,new ObjectMapper().createObjectNode(),List.of(line),List.of());
    }
    void setView(boolean confirmed) { doReturn(new WorkshopService.View(1,7,2,true,List.of(product(confirmed)))).when(service).view(1,auth); }
    @BeforeEach void setup() {
        db=mock(JdbcTemplate.class); reservations=mock(InventoryReservations.class); users=mock(UsersRepository.class);
        var user=new User(); var role=new Role(); role.setName("ROLE_FOREMAN"); user.setRoles(List.of(role));
        when(users.findByUsername("master")).thenReturn(Optional.of(user));
        auth=new UsernamePasswordAuthenticationToken("master","",List.of());
        service=spy(new WorkshopService(db,mock(CalculatedOrderService.class),mock(ProductionService.class),reservations,users,new ObjectMapper()));
        when(db.queryForList("SELECT OrderNo FROM orders WHERE OrderNo=? FOR UPDATE",Long.class,1L)).thenReturn(List.of(1L));
        when(db.queryForObject("SELECT production_finalized FROM orders WHERE OrderNo=?",Boolean.class,1L)).thenReturn(false);
        when(db.queryForObject("SELECT COUNT(*) FROM invoice_orders WHERE order_no=?",Long.class,1L)).thenReturn(0L);
        setView(false);
    }
    @Test void staleRevisionStopsBeforeTakingOverOrPosting() {
        assertThrows(PreviewException.class,()->service.complete(1,10,6,auth));
        verifyNoInteractions(reservations);
        verify(db,never()).update(eq("UPDATE orders SET workshop_mode=1 WHERE OrderNo=?"),anyLong());
    }
    @Test void anotherProductsIdCannotBeUsed() {
        assertThrows(PreviewException.class,()->service.complete(1,99,7,auth));
        verifyNoInteractions(reservations);
    }
    @Test void repeatedCompletionCannotPostAgain() {
        setView(true);
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));
        verifyNoInteractions(reservations);
    }
    @Test void otherProductsReservationsAreProtectedEvenWithinSameOrder() {
        when(db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,100L)).thenReturn(n("10"));
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,100L,10L)).thenReturn(n("6"));
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));
        verify(reservations).lockParts(new TreeSet<>(List.of(100L)));
        verify(reservations,never()).releaseProduct(anyLong(),anyString());
        verify(db,never()).update(eq("UPDATE parts SET OnHand=? WHERE PartNo=?"),any(),anyLong());
    }
    @Test void completionReleasesOnlySelectedProductAndPostsOnce() {
        when(db.queryForObject("SELECT CAST(OnHand AS DECIMAL(24,6)) FROM parts WHERE PartNo=?",BigDecimal.class,100L)).thenReturn(n("10"));
        when(db.queryForObject("SELECT COALESCE(SUM(reserved),0) FROM material_reservations WHERE part_no=? AND active=1 AND order_product_id<>?",BigDecimal.class,100L,10L)).thenReturn(n("5"));
        when(db.queryForObject("SELECT COUNT(*) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=0",Long.class,1L)).thenReturn(1L);
        when(db.queryForObject("SELECT COALESCE(SUM(actual_total),0) FROM order_products WHERE order_no=? AND active=1 AND production_confirmed=1",BigDecimal.class,1L)).thenReturn(n("50"));
        service.complete(1,10,7,auth);
        verify(reservations).releaseProduct(10,"master");
        verify(reservations,never()).release(anyLong(),anyString());
        verify(db).update("UPDATE parts SET OnHand=? WHERE PartNo=?",n("5"),100L);
        verify(db).update("UPDATE orders SET production_finalized=?,production_total=?,StatusOrder=CASE WHEN StatusOrder IN (1,2,3) THEN ? ELSE StatusOrder END WHERE OrderNo=?",false,n("50"),2,1L);
    }
    @Test void nonForemanCannotMutateEvenWithForgedAuthenticationAuthorities() {
        when(users.findByUsername("master")).thenReturn(Optional.empty());
        assertThrows(PreviewException.class,()->service.complete(1,10,7,auth));
        verifyNoInteractions(reservations);
    }
    @Test void measurementsRequireOnlyTheSelectedProductsLines() {
        var wrong=new ProductionService.Actual(999,n("1"),null,null);
        assertThrows(PreviewException.class,()->service.save(1,10,new WorkshopService.Correction(7,n("300"),n("400"),"Переміряв",List.of(wrong)),auth));
        verifyNoInteractions(reservations);
    }
}
