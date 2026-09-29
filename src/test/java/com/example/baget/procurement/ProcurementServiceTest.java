package com.example.baget.procurement;

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
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcurementServiceTest {
    JdbcTemplate db;UsersRepository users;InventoryReservations reserves;ProcurementService service;Authentication auth;
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    String key="c30b2f11-8dbf-47bd-91f4-317f40ad1af2";
    BigDecimal n(String s){return new BigDecimal(s);}
    Map<String,Object> header=new HashMap<>();
    @BeforeEach void setup(){
        db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);reserves=mock(InventoryReservations.class);
        var user=new User();var role=new Role();role.setName("ROLE_WAREHOUSE");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());
        when(users.findByUsername("store")).thenReturn(Optional.of(user));auth=new UsernamePasswordAuthenticationToken("store","",List.of());
        service=spy(new ProcurementService(db,users,reserves,json));
        header.put("id",9L);header.put("revision",3L);header.put("status","ORDERED");header.put("expectedDate",LocalDate.of(2026,10,1));
        when(db.queryForMap(startsWith("SELECT o.id,o.vendor_no"),eq(9L))).thenAnswer(i->new HashMap<>(header));
        when(db.queryForList(startsWith("SELECT id,part_no AS partNo"),eq(9L))).thenReturn(List.of(line(1,10,"METER","10","2"),line(2,20,"PIECE","8","0")));
        when(db.queryForMap(startsWith("SELECT PartNo AS partNo"),eq(10L))).thenReturn(part("METER","5"));
        when(db.queryForMap(startsWith("SELECT PartNo AS partNo"),eq(20L))).thenReturn(part("PIECE","0"));
        when(db.queryForObject("SELECT id FROM supplier_receipts WHERE request_key=?",Long.class,key)).thenReturn(11L);
        when(db.queryForObject("SELECT COUNT(*) FROM supplier_order_lines WHERE order_id=? AND received_qty<ordered_qty",Long.class,9L)).thenReturn(1L);
        doReturn(Map.of("id",9L)).when(service).read(9,auth);
    }
    Map<String,Object> line(long id,long part,String unit,String ordered,String received){return Map.of("id",id,"partNo",part,"unit",unit,"ordered",n(ordered),"received",n(received));}
    Map<String,Object> part(String unit,String stock){var m=new HashMap<String,Object>();m.put("unit",unit);m.put("stock",stock==null?null:n(stock));return m;}
    ProcurementService.Receipt receipt(long revision,ProcurementService.ReceivedLine...lines){return new ProcurementService.Receipt(key,revision,"Н-1",LocalDate.of(2026,9,28),"",List.of(lines));}
    ProcurementService.ReceivedLine qty(long id,String amount){return new ProcurementService.ReceivedLine(id,n(amount));}
    @Test void rolesAreReadFromDatabase(){when(users.findByUsername("store")).thenReturn(Optional.empty());assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"3")),auth));verifyNoInteractions(db,reserves);}
    @Test void staleRevisionDoesNotPostStock(){assertThrows(PreviewException.class,()->service.receive(9,receipt(2,qty(1,"3")),auth));verifyNoInteractions(reserves);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void foreignAndDuplicateLinesRejected(){assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(88,"1")),auth));assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"1"),qty(1,"1")),auth));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void overReceiptRejectedBeforeWrites(){assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"9")),auth));verifyNoInteractions(reserves);verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void invalidSecondLineCannotPartiallyPostFirst(){assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"3"),qty(2,"1.5")),auth));verify(db,never()).update(anyString(),any(Object[].class));verify(reserves,never()).refillPart(anyLong(),anyString());}
    @Test void unknownStockRequiresCount(){when(db.queryForMap(startsWith("SELECT PartNo AS partNo"),eq(10L))).thenReturn(part("METER",null));assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"3")),auth));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void unitChangeCannotSilentlyReceiveInDifferentUnits(){when(db.queryForMap(startsWith("SELECT PartNo AS partNo"),eq(10L))).thenReturn(part("SQUARE_METER","5"));assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"3")),auth));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void partialReceiptUpdatesStockBeforeAllocatingReserves(){
        service.receive(9,receipt(3,qty(1,"3")),auth);
        var in=inOrder(db,reserves);
        in.verify(reserves).lockParts(new TreeSet<>(List.of(10L)));
        in.verify(db).update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",n("8"),10L);
        in.verify(db).update("UPDATE supplier_order_lines SET received_qty=received_qty+? WHERE id=? AND order_id=?",n("3"),1L,9L);
        in.verify(db).update("UPDATE supplier_orders SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?","PARTIAL",9L);
        in.verify(reserves).refillPart(10,"store");
    }
    @Test void lastReceiptClosesDocument(){when(db.queryForObject("SELECT COUNT(*) FROM supplier_order_lines WHERE order_id=? AND received_qty<ordered_qty",Long.class,9L)).thenReturn(0L);service.receive(9,receipt(3,qty(1,"8"),qty(2,"8")),auth);verify(db).update("UPDATE supplier_orders SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?","RECEIVED",9L);}
    @Test void retryAfterResponseLossDoesNotPostAgainEvenWhenRevisionChanged() throws Exception {
        var r=receipt(3,qty(1,"3.250000"));header.put("revision",4L);header.put("status","PARTIAL");
        when(db.queryForList("SELECT order_id,actor,request_json FROM supplier_receipts WHERE request_key=?",key)).thenReturn(List.of(Map.of("order_id",9L,"actor","store","request_json",json.writeValueAsString(r))));
        service.receive(9,r,auth);verifyNoInteractions(reserves);verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void reusedKeyWithDifferentQuantityRejected() throws Exception {
        when(db.queryForList("SELECT order_id,actor,request_json FROM supplier_receipts WHERE request_key=?",key)).thenReturn(List.of(Map.of("order_id",9L,"actor","store","request_json",json.writeValueAsString(receipt(3,qty(1,"2"))))));
        assertThrows(PreviewException.class,()->service.receive(9,receipt(3,qty(1,"3")),auth));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void closedRemainderNeverReturnsReceivedStock(){header.put("status","PARTIAL");service.close(9,new ProcurementService.Action(3,"Недопоставка"),auth);verify(db).update("UPDATE supplier_orders SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?","CLOSED",9L);verifyNoInteractions(reserves);verify(db,never()).update(startsWith("UPDATE parts"),any(Object[].class));}
    @Test void draftNeedsDateBeforeOrdering(){header.put("status","DRAFT");header.put("expectedDate",null);assertThrows(PreviewException.class,()->service.place(9,new ProcurementService.Action(3,"Замовлено"),auth));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void blockedProfileCannotBeOrderedButExistingSupplyCanBeReceived() {
        header.put("status","DRAFT");
        when(db.queryForList("SELECT status FROM frame_purchase_policy WHERE part_no=?",String.class,10L)).thenReturn(List.of("BLOCKED"));
        assertThrows(PreviewException.class,()->service.place(9,new ProcurementService.Action(3,"Замовлено"),auth));
        verify(db,never()).update(anyString(),any(Object[].class));
        header.put("status","ORDERED");service.receive(9,receipt(3,qty(1,"3")),auth);
        verify(db).update("UPDATE parts SET OnHand=?,Version=COALESCE(Version,0)+1 WHERE PartNo=?",n("8"),10L);
    }
}
