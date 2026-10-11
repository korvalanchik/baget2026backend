package com.example.baget.telegram;
import java.util.*;
public final class TelegramCopyText {
 private TelegramCopyText(){}
 public static List<String> split(String text){
  var result=new ArrayList<String>();
  while(!text.isEmpty()){
   int end=Math.min(3500,text.length());
   if(end<text.length()){
    int newline=text.lastIndexOf('\n',end);if(newline>1750)end=newline+1;
    if(Character.isHighSurrogate(text.charAt(end-1)))end--;
   }
   result.add(text.substring(0,end));text=text.substring(end);
  }
  return result;
 }
}
