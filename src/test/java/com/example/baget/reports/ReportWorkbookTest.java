package com.example.baget.reports;
import org.junit.jupiter.api.Test;
import java.io.*;import java.nio.charset.StandardCharsets;import java.math.BigDecimal;import java.util.*;import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class ReportWorkbookTest {
 @Test void amountsAreNumericAndUserTextCannotBecomeFormula()throws Exception{
  byte[] bytes=ReportWorkbook.write(new ReportWorkbook.Table("Звіт",List.of("Період"),List.of(List.of("Разом",new BigDecimal("123.4567"))),List.of("Номер","Сума","Примітка"),List.of(List.of("001",new BigDecimal("123.4567"),"=HYPERLINK(\"x\")"),List.of("002","Не визначено","<script>&\u0001"))));
  Map<String,String> entries=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)entries.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}
  assertEquals(6,entries.size());String xml=entries.get("xl/worksheets/sheet1.xml");
  assertTrue(xml.contains("<v>123.4567</v>"));assertTrue(xml.contains("=HYPERLINK(&quot;x&quot;)"));assertFalse(xml.contains("<f>"));assertTrue(xml.contains("&lt;script&gt;&amp;"));assertFalse(xml.contains("\u0001"));assertTrue(xml.contains("Не визначено"));assertTrue(xml.contains("A6:C8"));assertTrue(xml.contains("topLeftCell=\"A7\""));
 }
}
