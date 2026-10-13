package com.example.baget.backup;
import com.example.baget.users.*;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BackupServiceTest {
 @Test void scheduleValidation(){
  assertDoesNotThrow(()->BackupService.validate(new BackupService.Settings(true,"WEEKLY",7,"23:59",14,0)));
  assertThrows(PreviewException.class,()->BackupService.validate(new BackupService.Settings(true,"DAILY",1,"24:00",14,0)));
  assertThrows(PreviewException.class,()->BackupService.validate(new BackupService.Settings(true,"DAILY",1,"03:00",1,0)));
  assertThrows(PreviewException.class,()->BackupService.validate(new BackupService.Settings(true,null,1,"03:00",14,0)));
 }
 @Test void unauthenticatedCannotReadOrWrite(){
  var db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var s=new BackupService(db,users);
  assertThrows(PreviewException.class,()->s.read(null));assertThrows(PreviewException.class,()->s.enqueue(UUID.randomUUID().toString(),null));verifyNoInteractions(db);
 }
 @Test void managerCannotDownload(){
  var db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var user=mock(User.class);var role=mock(Role.class);
  when(user.isEnabled()).thenReturn(true);when(role.getName()).thenReturn("ROLE_MANAGER");when(user.getRoles()).thenReturn(List.of(role));when(users.findByUsername("manager")).thenReturn(Optional.of(user));
  var a=new UsernamePasswordAuthenticationToken("manager","",List.of());
  assertThrows(PreviewException.class,()->new BackupService(db,users).file("id",a));verifyNoInteractions(db);
 }
 @Test void disabledAdminCannotRead(){
  var db=mock(JdbcTemplate.class);var users=mock(UsersRepository.class);var user=mock(User.class);
  when(users.findByUsername("admin")).thenReturn(Optional.of(user));
  assertThrows(PreviewException.class,()->new BackupService(db,users).read(new UsernamePasswordAuthenticationToken("admin","",List.of())));verifyNoInteractions(db);
 }
}
