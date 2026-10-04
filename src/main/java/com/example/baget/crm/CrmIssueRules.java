package com.example.baget.crm;
import java.util.Map;
public final class CrmIssueRules {
 private CrmIssueRules() {}
 public static String blocked(Map<String,Object> p){
  if("CUSTOMER".equals(p.get("location")))return "Уже видано клієнту";
  if(!"COMPLETED".equals(p.get("state")) || !truth(p.get("confirmed")))return "Виготовлення не підтверджено";
  if(!"BRANCH".equals(p.get("location")))return "Виріб ще не прийнято на філію";
  if(p.get("pending")!=null)return "Є незавершена передача. Спочатку завершіть або відкличте її";
  if(p.get("reworkId")!=null)return "Відкрита переробка";
  return null;
 }
 private static boolean truth(Object value){return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1;}
}
