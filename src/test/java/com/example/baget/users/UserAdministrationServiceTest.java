package com.example.baget.users;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserAdministrationServiceTest {
 JdbcTemplate db;UserAdministrationService service;PasswordEncoder encoder;
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("admin","",List.of());
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);encoder=mock(PasswordEncoder.class);service=new UserAdministrationService(db,encoder,new ObjectMapper());when(db.queryForList(startsWith("SELECT u.id FROM users"),eq(Long.class),eq("admin"))).thenReturn(List.of(1L));when(db.queryForList("SELECT id,name FROM roles")).thenReturn(List.of(Map.of("id",1L,"name","ROLE_ADMIN"),Map.of("id",2L,"name","ROLE_MANAGER")));when(db.queryForList("SELECT BranchNo FROM branches",Long.class)).thenReturn(List.of(5L));}
 UserAdministrationService.Edit edit(long id,boolean active,Set<Long> roles){return new UserAdministrationService.Edit(id,0,"admin","admin@example.test",active,"",roles,Set.of(5L));}
 void target(long id){when(db.queryForList("SELECT id,username,email,enabled,access_revision AS revision FROM users WHERE id=? FOR UPDATE",id)).thenReturn(List.of(new HashMap<>(Map.of("id",id,"username","admin","email","admin@example.test","enabled",true,"revision",0L))));}
 @Test void unauthorizedCannotRead(){when(db.queryForList(startsWith("SELECT u.id FROM users"),eq(Long.class),eq("admin"))).thenReturn(List.of());assertThrows(PreviewException.class,()->service.options(auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void selfCannotBeDisabled(){target(1);assertThrows(PreviewException.class,()->service.save(edit(1,false,Set.of(1L)),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void selfCannotLoseAdmin(){target(1);assertThrows(PreviewException.class,()->service.save(edit(1,true,Set.of(2L)),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void unknownRoleIsRejected(){assertThrows(PreviewException.class,()->service.save(edit(2,true,Set.of(999L)),auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void unknownBranchIsRejected(){var e=new UserAdministrationService.Edit(2L,0,"admin","admin@example.test",true,"",Set.of(2L),Set.of(999L));assertThrows(PreviewException.class,()->service.save(e,auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void staleEditCannotWrite(){target(2);var e=new UserAdministrationService.Edit(2L,3,"admin","admin@example.test",true,"",Set.of(1L),Set.of(5L));assertThrows(PreviewException.class,()->service.save(e,auth));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void lastAdministratorIsProtected(){target(2);when(db.queryForObject(startsWith("SELECT COUNT(*) FROM users"),eq(Long.class),eq(2L))).thenReturn(0L);assertThrows(PreviewException.class,()->service.save(edit(2,false,Set.of(1L)),auth));verify(db,never()).update(anyString(),any(Object[].class));}
}
