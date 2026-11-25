package com.example.baget.orderproduct;

import com.example.baget.my_company.CompanyDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Informational document generated from the current saved order snapshot; it is not a financial invoice. */
@Service
public class PreliminaryOrderInvoicePdf {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final CompanyDetailsService company;

    public PreliminaryOrderInvoicePdf(JdbcTemplate db, ObjectMapper json, CompanyDetailsService company) {
        this.db=db; this.json=json; this.company=company;
    }

    public byte[] render(long orderNo) throws Exception {
        var rows=db.queryForList("SELECT o.OrderNo,o.SaleDate,o.ShipDate,o.ItemsTotal,o.commerce_mode,c.Company,c.Mobile,c.Addr1,b.BranchName FROM orders o JOIN customer c ON c.CustNo=o.CustNo JOIN branches b ON b.BranchNo=o.BranchNo WHERE o.OrderNo=?",orderNo);
        if(rows.isEmpty() || !"RESELLER".equals(rows.get(0).get("commerce_mode")))
            throw new IllegalArgumentException("Попередній рахунок не знайдено");
        Map<String,Object> order=rows.get(0);
        List<Map<String,Object>> products=db.queryForList("SELECT client_product_id,configuration,total FROM order_products WHERE order_no=? AND active=1 ORDER BY id",orderNo);
        if(products.isEmpty()) throw new IllegalStateException("У замовленні немає активних виробів");

        var out=new ByteArrayOutputStream();
        var doc=new Document(PageSize.A4,36,36,28,36);
        PdfWriter.getInstance(doc,out); doc.open();
        var base=BaseFont.createFont(Objects.requireNonNull(getClass().getResource("/fonts/DejaVu_Sans.ttf")).toString(),BaseFont.IDENTITY_H,BaseFont.EMBEDDED);
        var font=new Font(base,10); var bold=new Font(base,11,Font.BOLD); var small=new Font(base,8);
        var supplier=company.get();
        doc.add(new Paragraph("Постачальник: "+supplier.getFullName(),bold));
        if(supplier.getEdrpou()!=null) doc.add(new Paragraph("ЄДРПОУ: "+supplier.getEdrpou(),font));
        if(supplier.getAddress()!=null) doc.add(new Paragraph(supplier.getAddress(),font));
        Paragraph heading=new Paragraph("ПОПЕРЕДНІЙ РАХУНОК № "+orderNo+" від "+date(order.get("SaleDate")),new Font(base,15,Font.BOLD));
        heading.setAlignment(Element.ALIGN_CENTER); heading.setSpacingBefore(18); heading.setSpacingAfter(8); doc.add(heading);
        Paragraph info=new Paragraph("ІНФОРМАЦІЙНИЙ ДОКУМЕНТ · НЕ Є ФІНАНСОВИМ РАХУНКОМ",bold);
        info.setAlignment(Element.ALIGN_CENTER); info.setSpacingAfter(14); doc.add(info);
        doc.add(new Paragraph("Замовник: "+value(order.get("Company")),font));
        if(order.get("Mobile")!=null) doc.add(new Paragraph("Телефон: "+order.get("Mobile"),font));
        if(order.get("Addr1")!=null && !order.get("Addr1").toString().isBlank()) doc.add(new Paragraph("Адреса: "+order.get("Addr1"),font));
        doc.add(new Paragraph("Філія: "+value(order.get("BranchName")),font));
        if(order.get("ShipDate")!=null) doc.add(new Paragraph("Планова дата готовності: "+date(order.get("ShipDate")),font));

        var table=new PdfPTable(new float[]{1,5,1.3f,1.7f}); table.setWidthPercentage(100); table.setSpacingBefore(16); table.setHeaderRows(1);
        for(String h: new String[]{"№","Виріб та розміри","Кількість","Вартість, грн"}) {
            PdfPCell cell=new PdfPCell(new Phrase(h,bold)); cell.setPadding(6); cell.setHorizontalAlignment(Element.ALIGN_CENTER); table.addCell(cell);
        }
        int index=0;
        BigDecimal sum=BigDecimal.ZERO;
        for(var product:products) {
            JsonNode root=json.readTree(product.get("configuration").toString()).path("draft").path("form");
            String label=label(root,index+1);
            table.addCell(cell(String.valueOf(++index),font,Element.ALIGN_CENTER));
            table.addCell(cell(label,small,Element.ALIGN_LEFT));
            table.addCell(cell(String.valueOf(root.path("productQuantity").asInt(1)),font,Element.ALIGN_RIGHT));
            BigDecimal amount=decimal(product.get("total")); sum=sum.add(amount);
            table.addCell(cell(money(amount),font,Element.ALIGN_RIGHT));
        }
        doc.add(table);
        Paragraph total=new Paragraph("Разом: "+money(sum)+" грн",new Font(base,13,Font.BOLD));
        total.setAlignment(Element.ALIGN_RIGHT); total.setSpacingBefore(12); doc.add(total);
        Paragraph note=new Paragraph("Вартість сформована за роздрібними цінами філії-посередника на момент останнього збереження замовлення. Документ має довідковий характер; розрахунки з фірмою-посередником здійснюються окремо.",font);
        note.setSpacingBefore(22); doc.add(note);
        doc.close(); return out.toByteArray();
    }

    private String label(JsonNode form,int index) {
        String kind=switch(form.path("artworkType").asText("")) {
            case "MIRROR" -> "Дзеркало";
            case "PHOTO" -> "Фотографія";
            case "PICTURE" -> "Картина";
            case "EMBROIDERY" -> "Вишивка";
            default -> "Виріб";
        };
        BigDecimal width=decimal(form.path("widthMm")),height=decimal(form.path("heightMm"));
        JsonNode margins=form.path("margins");
        if(margins.isObject()) {
            width=width.add(decimal(margins.path("leftMm"))).add(decimal(margins.path("rightMm")));
            height=height.add(decimal(margins.path("topMm"))).add(decimal(margins.path("bottomMm")));
        }
        return kind+" №"+index+" · "+dimension(width)+" × "+dimension(height)+" мм"+(form.path("withFrame").asBoolean(true)?" · у багеті":"");
    }
    private PdfPCell cell(String value,Font font,int align){var c=new PdfPCell(new Phrase(value,font));c.setPadding(6);c.setHorizontalAlignment(align);c.setVerticalAlignment(Element.ALIGN_MIDDLE);return c;}
    private BigDecimal decimal(Object value){if(value==null)return BigDecimal.ZERO;return new BigDecimal(value.toString());}
    private BigDecimal decimal(JsonNode value){return value==null||!value.isNumber()?BigDecimal.ZERO:value.decimalValue();}
    private String money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private String dimension(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
    private String value(Object v){return v==null?"—":v.toString();}
    private String date(Object v){if(v==null)return "—";String s=v.toString();try{return LocalDate.parse(s.substring(0,10)).format(DATE);}catch(Exception e){return s;}}
}
