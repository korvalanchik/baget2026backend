package com.example.baget.finance;

import com.example.baget.branch.Branch;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
class CustomerPaymentAccessTest {
 JdbcTemplate db;UsersRepository users;CustomerPaymentAccess access;User manager;
 @BeforeEach void setup(){
  db=mock(JdbcTemplate.class);users=mock(UsersRepository.class);access=new CustomerPaymentAccess(users,db);
  manager=new User();Role r=new Role();r.setName("ROLE_MANAGER");manager.setRoles(List.of(r));
  Branch b=new Branch();b.setBranchNo(1L);manager.setAllowedBranches(Set.of(b));
  when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
 }
 @Test void managerCanAcceptPayments(){assertSame(manager,access.user(new UsernamePasswordAuthenticationToken("manager","",List.of())));}
 @Test void workshopRoleAloneCannotAcceptPayments(){Role role=new Role();role.setName("ROLE_FOREMAN");manager.setRoles(List.of(role));assertThrows(PreviewException.class,()->access.user(new UsernamePasswordAuthenticationToken("manager","",List.of())));}
 @Test void anotherBranchDeniedBeforeCustomerQuery(){assertThrows(PreviewException.class,()->access.customer(manager,10,2));verifyNoInteractions(db);}
 @Test void customerMustBelongToAccessibleBranch(){when(db.queryForObject(anyString(),eq(Long.class),eq(10L),eq(1L),eq(1L),eq(1L),eq(1L))).thenReturn(0L);assertThrows(PreviewException.class,()->access.customer(manager,10,1));}
 @Test void mixedBranchInvoiceRequiresBothBranches(){when(db.queryForList(startsWith("SELECT DISTINCT o.BranchNo"),eq(Long.class),eq(5L))).thenReturn(List.of(1L,2L));assertThrows(PreviewException.class,()->access.invoice(manager,5));}
}
