package com.example.baget.reconciliation;
import com.example.baget.my_company.CompanyDetailsService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

@Service
public class PartnerReconciliationPdf {
 private final CompanyDetailsService company;
 public PartnerReconciliationPdf(CompanyDetailsService company){this.company=company;}
 @SuppressWarnings("unchecked")
 public byte[] render(Map<String,Object> report) throws Exception {
  var out=new ByteArrayOutputStream();var doc=new Document(PageSize.A4.rotate(),30,30,30,40);var writer=PdfWriter.getInstance(doc,out);
  var base=BaseFont.createFont(Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),BaseFont.IDENTITY_H,BaseFont.EMBEDDED);
  var font=new Font(base,9);var bold=new Font(base,10,Font.BOLD);
  writer.setPageEvent(new PdfPageEventHelper(){@Override public void onEndPage(PdfWriter w,Document d){ColumnText.showTextAligned(w.getDirectContent(),Element.ALIGN_RIGHT,new Phrase("Сторінка "+w.getPageNumber(),font),d.right(),22,0);}});
  doc.open();doc.addTitle("Звірка з фірмою-посередником");
  doc.add(new Paragraph("Звірка розрахунків з фірмою-посередником",new Font(base,16,Font.BOLD)));
  doc.add(new Paragraph("Підприємство: "+company.get().getFullName(),bold));
  doc.add(new Paragraph("Фірма: "+report.get("partnerName")+" | Період: "+report.get("from")+" — "+report.get("to"),bold));
  doc.add(new Paragraph("Сформовано: "+report.get("generatedAt")+". Часовий пояс: Київ. Валюта: грн.",font));
  var sums=(Map<String,BigDecimal>)report.get("totals");
  doc.add(new Paragraph("Початкове сальдо: "+money(sums.get("opening"))+" | Нараховано: "+money(sums.get("invoiced"))+" | Отримано коштів: "+money(sums.get("received"))+" | Повернено: "+money(sums.get("refunded")),font));
  doc.add(new Paragraph("Інші зміни сальдо: "+money(sums.get("otherChange"))+" | Кінцеве сальдо: "+money(sums.get("closing")),bold));
  doc.add(new Paragraph("Доступний аванс зараз (механізм оплат): "+money(report.get("availableAdvanceNow"))+". Сальдо проводок без рахунку на кінець періоду: "+money(report.get("unallocatedLedgerAtEnd")),font));
  for(String warning:(java.util.List<String>)report.get("warnings"))doc.add(new Paragraph(warning,font));
  var table=table(new float[]{2,4,2,2,2,2},new String[]{"Дата / філія","Операція / примітка","Документ","Збільшення боргу","Зменшення боргу","Сальдо"},bold);
  for(var row:(java.util.List<Map<String,Object>>)report.get("entries")){
   cell(table,text(row.get("date"))+"\n"+text(row.get("branchName")),font);
   cell(table,text(row.get("label"))+"\n"+text(row.get("note")),font);
   cell(table,row.get("invoiceNo")!=null?"Рах. № "+row.get("invoiceNo"):text(row.get("reference")),font);
   cell(table,"OUT".equals(row.get("direction"))?money(row.get("amount")):"",font);cell(table,"IN".equals(row.get("direction"))?money(row.get("amount")):"",font);cell(table,money(row.get("balance")),font);
  }
  if(((java.util.List<?>)report.get("entries")).isEmpty()){cell(table,"Проводок у періоді немає",font);for(int i=0;i<5;i++)cell(table,"",font);}
  doc.add(table);doc.add(new Paragraph("Обороти: збільшення боргу "+money(sums.get("debit"))+"; зменшення боргу "+money(sums.get("credit"))+". Включають внутрішні перенесення.",font));
  var invoices=(java.util.List<Map<String,Object>>)report.get("invoices");
  if(!invoices.isEmpty()){
   doc.newPage();doc.add(new Paragraph("Розшифрування рахунків із проводок періоду",bold));
   doc.add(new Paragraph("Суми документів не підсумовуються повторно: об’єднаний рахунок містить перенесений борг. Для старих рахунків деталізація доступна до замовлення.",font));
   for(var invoice:invoices){
    doc.add(new Paragraph("Рахунок № "+invoice.get("invoiceNo")+" | Сума документа: "+money(invoice.get("amount"))+" | Сальдо проводок рахунку на кінець періоду: "+money(invoice.get("ledgerBalanceAtEnd")),bold));
    var lines=table(new float[]{3,2,2,2,3},new String[]{"Філія","Замовлення","Виріб","До рахунку","Перенесено з рахунку"},bold);
    for(var row:(java.util.List<Map<String,Object>>)invoice.get("lines")){cell(lines,text(row.get("branchName")),font);cell(lines,text(row.get("orderNo")),font);cell(lines,text(row.get("productId")),font);cell(lines,money(row.get("amount")),font);cell(lines,text(row.get("sourceInvoiceNo")),font);}
    if(((java.util.List<?>)invoice.get("lines")).isEmpty())doc.add(new Paragraph("Деталізація відсутня",font));else doc.add(lines);
   }
  }
  doc.add(new Paragraph("Довідкова звірка за проводками підприємства. Кошти кінцевих клієнтів у посередника сюди не входять. Документ не є підтвердженням погодження другою стороною.",font));
  doc.close();return out.toByteArray();
 }
 private static PdfPTable table(float[] widths,String[] titles,Font font){var table=new PdfPTable(widths);table.setWidthPercentage(100);table.setHeaderRows(1);table.setSpacingBefore(10);table.setSpacingAfter(10);table.setSplitRows(true);table.setSplitLate(false);for(String t:titles)cell(table,t,font);return table;}
 private static void cell(PdfPTable table,String text,Font font){var cell=new PdfPCell(new Phrase(text,font));cell.setPadding(5);table.addCell(cell);}
 private static String text(Object value){return value==null?"":value.toString();}
 private static String money(Object value){return value==null?"0.00":new BigDecimal(value.toString()).setScale(2,RoundingMode.HALF_UP).toPlainString();}
}
