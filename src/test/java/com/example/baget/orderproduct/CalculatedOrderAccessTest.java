package com.example.baget.orderproduct;

import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.product.ProductPreviewRequest;
import com.example.baget.customer.CustomerService;
import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculatedOrderAccessTest {
    private ValidatorFactory factory;
    private JdbcTemplate db;
    private UsersRepository users;
    private EstimatePreviewService preview;
    private InventoryReservations reservations;
    private CalculatedOrderService service;
    @BeforeEach void setup(){
        factory=Validation.buildDefaultValidatorFactory();db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);
        preview=mock(EstimatePreviewService.class);reservations=mock(InventoryReservations.class);
        service=new CalculatedOrderService(db,preview,reservations,users,mock(CustomerService.class),new ObjectMapper(),factory.getValidator(),mock(EntityManager.class));
    }
    @AfterEach void close(){factory.close();}
    private CalculatedOrderRequest request(){
        var p=new ProductPreviewRequest(ProductPreviewRequest.ProductType.FRAMED_ARTWORK,new BigDecimal("400"),new BigDecimal("500"),1,30410L,null);
        var component=new EstimatePreviewRequest.Item("art-1",EstimatePreviewRequest.Kind.PRODUCT,p,null);
        var draft=new CalculatedOrderRequest.Product("art-1",new ObjectMapper().createObjectNode(),"",List.of(component));
        return new CalculatedOrderRequest("12345678-1234-4234-8234-123456789012","Парапет",9878L,"Test","0660000000","","",null,false,0L,List.of(draft));
    }
    @Test void anonymousCannotReserveOrWrite(){
        assertEquals("AUTH_REQUIRED",assertThrows(PreviewException.class,()->service.create(request(),null)).getCode());
        verifyNoInteractions(db,preview,reservations);
    }
    @Test void authorizedRoleStillNeedsBranchAccess(){
        var user=new User();var role=new Role();role.setName("ROLE_LEVEL2");user.setRoles(List.of(role));user.setAllowedBranches(Set.of());
        when(users.findByUsername("manager")).thenReturn(Optional.of(user));
        var auth=new UsernamePasswordAuthenticationToken("manager","unused",List.of());
        assertEquals("BRANCH_ACCESS_DENIED",assertThrows(PreviewException.class,()->service.create(request(),auth)).getCode());
        verifyNoInteractions(db,preview,reservations);
    }
    @Test void malformedRequestCannotCreatePartialOrder(){
        assertThrows(PreviewException.class,()->service.create(null,null));
        verifyNoInteractions(db,preview,reservations,users);
    }
}
