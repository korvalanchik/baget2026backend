package com.example.baget.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Service
public class TelegramCopySender {
 private final ObjectMapper json;private final String token;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public TelegramCopySender(ObjectMapper json,@Value("${bot.token:}") String token){this.json=json;this.token=token;}
 public record Result(Long messageId,String error,boolean permanent,long retryAfter){}
 public Result send(long chat,String text){
  if(token.isBlank())return new Result(null,"Токен бота не налаштовано",true,0);
  try{
   var request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/sendMessage")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("chat_id",chat,"text",text)))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());var data=json.readTree(response.body());
   if(response.statusCode()==200 && data.path("ok").asBoolean() && data.path("result").path("message_id").isNumber())
    return new Result(data.path("result").path("message_id").asLong(),null,false,0);
   int code=data.path("error_code").asInt(response.statusCode());
   String error=switch(code){case 403->"Бот заблокований або немає доступу до чату";case 400->"Чат не знайдено або некоректне повідомлення";case 401,404->"Перевірте токен бота";case 429->"Ліміт Telegram. Повторимо пізніше";default->"Telegram тимчасово недоступний ("+code+")";};
   return new Result(null,error,code==400 || code==401 || code==403 || code==404,Math.max(0,data.path("parameters").path("retry_after").asLong(0)));
  }catch(InterruptedException e){Thread.currentThread().interrupt();return new Result(null,"Надсилання перервано; результат невідомий",false,0);}
   catch(Exception e){return new Result(null,"Немає підтвердження доставки; повтор може створити копію",false,0);}
 }
}
