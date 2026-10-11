package com.example.baget.telegram;

import com.example.baget.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramCopiesTest {
 @Test void longPlainTextPreservesUnicodeAndAllContent(){
  String input="x".repeat(3499)+"😀"+"<tag>&".repeat(1500);
  var chunks=TelegramCopyText.split(input);
  assertEquals(input,String.join("",chunks));
  assertTrue(chunks.stream().allMatch(s->s.length()<=3500&&!Character.isHighSurrogate(s.charAt(s.length()-1))));
 }
 @Test void noTokenIsPermanentFailureWithoutNetwork(){assertTrue(new TelegramCopySender(new ObjectMapper(),"").send(1,"test").permanent());}
 @Test void managerCannotChangeSettings(){
  var users=mock(UsersRepository.class);var db=mock(JdbcTemplate.class);var u=new User();var role=new Role();role.setName("ROLE_MANAGER");u.setRoles(List.of(role));u.setEnabled(true);
  when(users.findByUsername("manager")).thenReturn(Optional.of(u));var settings=new TelegramCopySettings(db,users);
  assertThrows(ResponseStatusException.class,()->settings.save(new TelegramCopySettings.Update(true,0,List.of()),new UsernamePasswordAuthenticationToken("manager","",List.of())));
  verifyNoInteractions(db);
 }
 @Test void disabledSettingDoesNotCreateOutbox(){
  var db=mock(JdbcTemplate.class);when(db.queryForObject("SELECT enabled FROM telegram_copy_settings WHERE id=1",Boolean.class)).thenReturn(false);
  var users=mock(UsersRepository.class);new TelegramOrderCopies(db,users,new ObjectMapper(),"").created(new OrderCopyCreated(1));
  verifyNoInteractions(users);verify(db,never()).update(anyString(),any(Object[].class));
 }
 static User recipient(long id,long chat,String roleName){
  var u=new User();u.setId(id);u.setTelegramId(chat);u.setUsername("user"+id);u.setEnabled(true);var r=new Role();r.setName(roleName);u.setRoles(List.of(r));return u;
 }
 @Test void resellerSelectionUsesExplicitRepresentatives(){
  var users=mock(UsersRepository.class);var author=recipient(1,101,"ROLE_MANAGER");var admin=recipient(2,102,"ROLE_ADMIN");var representative=recipient(3,103,"ROLE_COUNTER");var other=recipient(4,104,"ROLE_COUNTER");
  when(users.findAll()).thenReturn(List.of(author,admin,representative,other));
  class RecipientsDb extends JdbcTemplate {
   String mode="RESELLER";
   @Override public List<Map<String,Object>> queryForList(String sql,Object...args){return List.of(Map.of("EmpNo",1L,"commerce_mode",mode,"commerce_partner_id",8L));}
   @Override public <T>List<T> queryForList(String sql,Class<T> type,Object...args){assertEquals(8L,args[0]);return List.of(type.cast(3L));}
  }
  var db=new RecipientsDb();var copies=new TelegramOrderCopies(db,users,new ObjectMapper(),"102");
  assertTrue(copies.allowed(42,101));assertTrue(copies.allowed(42,102));assertTrue(copies.allowed(42,103));assertFalse(copies.allowed(42,104));
  db.mode="OWN";assertFalse(copies.allowed(42,103));author.setEnabled(false);assertFalse(copies.allowed(42,101));
 }
 static class WorkerDb extends JdbcTemplate {

  boolean enabled = true;
  int attempts = 0;
  String sql;
  Object[] args;

  @Override
  public <T> T queryForObject(
          String sql,
          Class<T> type
  ) {
   return type.cast(enabled);
  }

  @Override
  public List<Map<String, Object>> queryForList(
          String sql
  ) {
   return List.of(
           Map.of(
                   "id", 1L,
                   "order_no", 42L,
                   "chat_id", 12L,
                   "body", "copy",
                   "attempts", attempts
           )
   );
  }

  @Override
  public int update(String sql, Object... args) {
   this.sql = sql;
   this.args = args;
   return 1;
  }
 } @Test void deliveryRecordsSuccessAndPausesWhenDisabled(){
  var db=new WorkerDb();var copies=mock(TelegramOrderCopies.class);var sender=mock(TelegramCopySender.class);when(copies.allowed(42,12)).thenReturn(true);when(sender.send(12,"copy")).thenReturn(new TelegramCopySender.Result(55L,null,false,0));
  var worker=new TelegramCopyWorker(db,mock(org.springframework.transaction.PlatformTransactionManager.class),copies,sender);worker.deliver();assertTrue(db.sql.contains("state='SENT'"));assertEquals(55L,db.args[1]);db.enabled=false;worker.deliver();verify(sender,times(1)).send(12,"copy");
 }
 @Test void transientErrorsRetryButFifthAttemptStops(){
  var db=new WorkerDb();var copies=mock(TelegramOrderCopies.class);var sender=mock(TelegramCopySender.class);when(copies.allowed(42,12)).thenReturn(true);when(sender.send(12,"copy")).thenReturn(new TelegramCopySender.Result(null,"rate",false,120));var worker=new TelegramCopyWorker(db,mock(org.springframework.transaction.PlatformTransactionManager.class),copies,sender);
  worker.deliver();assertEquals("PENDING",db.args[0]);assertEquals(120L,db.args[3]);db.attempts=4;worker.deliver();assertEquals("FAILED",db.args[0]);
 }
 @Test void revokedRecipientDoesNotReceiveQueuedCopy(){
  var db=new WorkerDb();var copies=mock(TelegramOrderCopies.class);var sender=mock(TelegramCopySender.class);when(copies.allowed(42,12)).thenReturn(false);
  new TelegramCopyWorker(db,mock(org.springframework.transaction.PlatformTransactionManager.class),copies,sender).deliver();verifyNoInteractions(sender);assertTrue(db.sql.contains("SKIPPED"));
 }
}
