package com.example.baget.telegram;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class TelegramCopyWorker {
 @Configuration @EnableScheduling static class Scheduling {}
 private final TelegramOrderCopies copies;private final TelegramCopySender sender;
 private final JdbcTemplate db;private final TransactionTemplate tx;
 public TelegramCopyWorker(JdbcTemplate db,PlatformTransactionManager tm,TelegramOrderCopies copies,TelegramCopySender sender){this.copies=copies;this.sender=sender;this.db=db;this.tx=new TransactionTemplate(tm);}
 @Scheduled(fixedDelay=2000,initialDelay=15000)
 public void tick(){tx.executeWithoutResult(status->deliver());}
 void deliver(){
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT enabled FROM telegram_copy_settings WHERE id=1 FOR SHARE",Boolean.class)))return;
  // One worker per row across instances. Earlier parts must be delivered first.
  var rows=db.queryForList("SELECT q.* FROM telegram_order_outbox q WHERE q.state='PENDING' AND q.next_attempt<=CURRENT_TIMESTAMP AND NOT EXISTS (SELECT 1 FROM telegram_order_outbox prev WHERE prev.order_no=q.order_no AND prev.chat_id=q.chat_id AND prev.part_no<q.part_no AND prev.state<>'SENT') ORDER BY q.id LIMIT 1 FOR UPDATE SKIP LOCKED");
  if(rows.isEmpty())return;var row=rows.get(0);long id=((Number)row.get("id")).longValue();int attempt=((Number)row.get("attempts")).intValue()+1;
  long chat=((Number)row.get("chat_id")).longValue();
  if(!copies.allowed(((Number)row.get("order_no")).longValue(),chat)){
   db.update("UPDATE telegram_order_outbox SET state='SKIPPED',last_error='Отримувач більше не має права на копію' WHERE order_no=? AND chat_id=? AND state IN ('PENDING','FAILED')",row.get("order_no"),chat);return;
  }
  var result=sender.send(chat,row.get("body").toString());
  if(result.messageId()!=null){db.update("UPDATE telegram_order_outbox SET state='SENT',attempts=?,message_id=?,sent_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?",attempt,result.messageId(),id);return;}
  long delay=Math.max(Math.min(3600,30L*(1L<<Math.min(attempt,6))),Math.min(86400,result.retryAfter()));
  db.update("UPDATE telegram_order_outbox SET state=?,attempts=?,last_error=?,next_attempt=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP) WHERE id=?",result.permanent() || attempt>=5?"FAILED":"PENDING",attempt,result.error(),delay,id);
 }
}
