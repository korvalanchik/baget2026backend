package com.example.baget.billing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import com.example.baget.calculation.preview.PreviewException;
class ResellerInvoiceSnapshotTest {
 final ObjectMapper json=new ObjectMapper();
 JsonNode source() throws Exception {return json.readTree("""
 {"total":100,"lines":[{"itemNo":1,"partNo":10,"actual":2.5,"role":"FRAME"},{"itemNo":2,"partNo":20,"actual":1,"role":"STRETCH_WORK"}]}
 """);}
 List<Map<String,Object>> rates(){return List.of(Map.of("itemNo",1L,"partNo",10L,"price",new BigDecimal("10.125")),Map.of("itemNo",2L,"partNo",20L,"price",new BigDecimal("5")));}
 @Test void freezesWholesaleAndKeepsRetailAndSourceIntact() throws Exception {
  var source=source();var result=ResellerInvoiceSnapshot.build(source,rates());
  assertEquals(new BigDecimal("30.31"),result.path("resellerTotal").decimalValue());
  assertEquals(100,result.path("retailTotal").asInt());assertEquals(100,result.path("total").asInt());
  assertFalse(source.has("resellerTotal"));assertEquals(2,result.path("resellerLines").size());
 }
 @Test void missingSavedRateCannotFallBackToRetailOrCurrentCatalog() throws Exception {
  var rates=new ArrayList<>(rates());var missing=new HashMap<>(rates.get(0));missing.put("price",null);rates.set(0,missing);
  assertThrows(PreviewException.class,()->ResellerInvoiceSnapshot.build(source(),rates));
 }
 @Test void differentMaterialCannotUseAnotherSavedPrice() throws Exception {
  var rates=new ArrayList<>(rates());rates.set(0,Map.of("itemNo",1L,"partNo",999L,"price",BigDecimal.ONE));
  assertThrows(PreviewException.class,()->ResellerInvoiceSnapshot.build(source(),rates));
 }
 @Test void workshopDefectHasNoExtraCharge() throws Exception {
  var source=json.readTree("{\"total\":0,\"lines\":[{\"itemNo\":1,\"partNo\":10,\"actual\":2,\"role\":\"REWORK_INTERNAL\"}]}");
  var rate=new HashMap<String,Object>();rate.put("itemNo",1L);rate.put("partNo",10L);rate.put("price",null);
  assertEquals(0,ResellerInvoiceSnapshot.build(source,List.of(rate)).path("resellerTotal").decimalValue().signum());
 }
 @Test void unconfirmedQuantityIsRejected() throws Exception {
  var source=source();((com.fasterxml.jackson.databind.node.ObjectNode)source.path("lines").get(0)).putNull("actual");
  assertThrows(PreviewException.class,()->ResellerInvoiceSnapshot.build(source,rates()));
 }
}
