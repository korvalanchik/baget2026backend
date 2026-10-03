package com.example.baget.billing;
import com.example.baget.my_company.CompanyDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
@Service
public class ProductInvoicePdf {
 private final CompanyDetailsService company;
 public ProductInvoicePdf(CompanyDetailsService company){this.company=company;}
 @SuppressWarnings("unchecked")
 public byte[] render(Map<String,Object> invoice) throws Exception {
  var out=new ByteArrayOutputStream();var doc=new Document(PageSize.A4,36,36,40,40);PdfWriter.getInstance(doc,out);doc.open();
  var base=BaseFont.createFont(Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),BaseFont.IDENTITY_H,BaseFont.EMBEDDED);
  var font=new Font(base,10);var bold=new Font(base,11,Font.BOLD);var supplier=company.get();
  doc.add(new Paragraph("Постачальник: "+supplier.getFullName(),bold));
  if(supplier.getEdrpou()!=null)doc.add(new Paragraph("ЄДРПОУ: "+supplier.getEdrpou(),font));
  if(supplier.getBankAccount()!=null)doc.add(new Paragraph(supplier.getBankAccount(),font));
  if(supplier.getAddress()!=null)doc.add(new Paragraph(supplier.getAddress(),font));
  doc.add(new Paragraph("Рахунок № "+invoice.get("invoiceNo"),bold));
  doc.add(new Paragraph("Дата: "+invoice.get("createdAt"),font));
  doc.add(new Paragraph("Платник: "+invoice.get("payerName"),font));
  if(invoice.get("partnerId")!=null)doc.add(new Paragraph("Рахунок фірмі-посереднику за реселлерськими цінами",bold));
  if("MERGED".equals(invoice.get("lifecycle")))doc.add(new Paragraph("Архівний рахунок: залишок перенесено до іншого рахунку",bold));
  var table=new PdfPTable(new float[]{2,4,2,2});table.setWidthPercentage(100);table.setSpacingBefore(15);table.setHeaderRows(1);
  for(String h:new String[]{"Замовлення","Виріб / підстава","Вартість виробу","До цього рахунку"})table.addCell(new Phrase(h,bold));
  for(var line:(java.util.List<Map<String,Object>>)invoice.get("products")){
   var snap=(JsonNode)line.get("snapshot");
   String label="Виріб "+line.get("productId")+" (екз.: "+snap.path("configuration").path("draft").path("form").path("productQuantity").asInt(1)+")"+"; "+snap.path("widthMm").asText("—")+" × "+snap.path("heightMm").asText("—")+" мм";
   if(line.get("sourceInvoiceNo")!=null)label+="; залишок з № "+line.get("sourceInvoiceNo");
   table.addCell(new Phrase(line.get("orderNo").toString(),font));table.addCell(new Phrase(label,font));
   table.addCell(new Phrase(line.get("originalAmount").toString(),font));table.addCell(new Phrase(line.get("amount").toString(),font));
  }
  doc.add(table);doc.add(new Paragraph("Сума рахунку: "+invoice.get("amount")+" грн",bold));
  doc.add(new Paragraph("Залишок до оплати на момент друку: "+((BigDecimal)invoice.get("debt")).max(BigDecimal.ZERO)+" грн",font));
  doc.add(new Paragraph("Рахунок охоплює лише перелічені вироби. При об’єднанні переносяться залишки боргу після оплат та зарахувань авансу.",font));
  if(invoice.get("note")!=null)doc.add(new Paragraph(invoice.get("note").toString(),font));doc.close();return out.toByteArray();
 }
}
