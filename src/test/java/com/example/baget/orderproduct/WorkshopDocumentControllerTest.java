package com.example.baget.orderproduct;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.commercial.CommercialService;
import com.example.baget.qrcoding.QrGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WorkshopDocumentControllerTest {
 final WorkshopService workshop=mock(WorkshopService.class);
 final CalculatedOrderService orders=mock(CalculatedOrderService.class);
 final CommercialService commercial=mock(CommercialService.class);
 final JdbcTemplate db=mock(JdbcTemplate.class);
 final QrGenerator qr=mock(QrGenerator.class);
 final Authentication auth=mock(Authentication.class);
 final WorkshopDocumentController controller=new WorkshopDocumentController(workshop,orders,commercial,db,qr);
 @Test void branchDenialStopsDocumentBeforeAnyOtherReads(){when(workshop.view(42L,auth)).thenThrow(new PreviewException(HttpStatus.FORBIDDEN,"DENIED","Denied"));assertThrows(PreviewException.class,()->controller.read(42L,7L,auth));verifyNoInteractions(orders,commercial,db,qr);}
 @Test void cannotPrintAnotherOrdersProduct(){var view=mock(WorkshopService.View.class);when(view.products()).thenReturn(List.of());when(workshop.view(42L,auth)).thenReturn(view);assertThrows(PreviewException.class,()->controller.read(42L,7L,auth));verifyNoInteractions(orders,commercial,db,qr);}
 @Test void unfinishedSheetHasNoQrAndHeaderHasNoCustomerFinance()throws Exception{
  var view=mock(WorkshopService.View.class);var p=mock(WorkshopService.Product.class);var order=mock(CalculatedOrderService.View.class);
  when(p.id()).thenReturn(7L);when(p.state()).thenReturn(WorkshopRules.State.ACCEPTED);when(view.products()).thenReturn(List.of(p));when(view.status()).thenReturn(2);when(workshop.view(42L,auth)).thenReturn(view);when(orders.read(42L,auth)).thenReturn(order);when(order.branchName()).thenReturn("Own");
  var result=(Map<?,?>)controller.read(42L,7L,auth).getBody();assertNotNull(result);assertEquals(false,result.get("labelAllowed"));assertFalse(result.containsKey("qrImage"));var header=(Map<?,?>)result.get("header");assertFalse(header.containsKey("phone"));assertFalse(header.containsKey("company"));assertFalse(header.containsKey("total"));verifyNoInteractions(db,qr);
 }
 @Test void labelRequiresConfirmedCompletedNonCancelledWithoutRework(){assertTrue(WorkshopDocumentController.labelAllowed(3,WorkshopRules.State.COMPLETED,true,false));assertFalse(WorkshopDocumentController.labelAllowed(3,WorkshopRules.State.COMPLETED,false,false));assertFalse(WorkshopDocumentController.labelAllowed(3,WorkshopRules.State.COMPLETED,true,true));assertFalse(WorkshopDocumentController.labelAllowed(5,WorkshopRules.State.COMPLETED,true,false));assertFalse(WorkshopDocumentController.labelAllowed(10,WorkshopRules.State.COMPLETED,true,false));assertFalse(WorkshopDocumentController.labelAllowed(2,WorkshopRules.State.IN_PROGRESS,true,false));}
}
