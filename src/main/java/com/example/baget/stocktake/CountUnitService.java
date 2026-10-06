package com.example.baget.stocktake;

import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.procurement.ProcurementRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

@Service
public class CountUnitService {
 private final JdbcTemplate db;
 public CountUnitService(JdbcTemplate db){this.db=db;}
 public record Entry(@Positive Long presetId,@NotNull @Min(0) @Max(1000000) Integer count,
  @Positive @DecimalMax("100000") @Digits(integer=6,fraction=3) BigDecimal widthMm,
  @Positive @DecimalMax("100000") @Digits(integer=6,fraction=3) BigDecimal heightMm) {}
 public record Input(@NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal remainder,
  @NotNull @Size(max=100) List<@NotNull @Valid Entry> entries) {}
 public record Result(Input input,String unit,List<Map<String,Object>> entries,BigDecimal total) {}
 public Result calculate(long part,String unit,Input input){
  try{ProcurementRules.quantity(unit,input.remainder(),true);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
  var rows=new ArrayList<Map<String,Object>>();BigDecimal total=input.remainder();
  for(var entry:input.entries()){
   BigDecimal factor;String label;
   if(entry.presetId()!=null){
    if(entry.widthMm()!=null || entry.heightMm()!=null)throw bad("Оберіть одиницю або розміри обрізка, не обидва варіанти");
    // Versions are immutable. An archived version remains usable by existing drafts.
    var presets=db.queryForList("SELECT label,base_unit,factor FROM material_count_units WHERE id=? AND part_no=?",entry.presetId(),part);
    if(presets.isEmpty() || !unit.equals(presets.get(0).get("base_unit")))throw bad("Одиниця не відповідає матеріалу");
    factor=new BigDecimal(presets.get(0).get("factor").toString());label=presets.get(0).get("label").toString();
   }else{
    if(!"SQUARE_METER".equals(unit)||entry.widthMm()==null||entry.heightMm()==null)throw bad("Для прямокутного обрізка вкажіть ширину та висоту в мм");
    factor=entry.widthMm().multiply(entry.heightMm()).movePointLeft(6);
    label="Обрізок "+entry.widthMm().toPlainString()+" × "+entry.heightMm().toPlainString()+" мм";
   }
   BigDecimal subtotal=factor.multiply(BigDecimal.valueOf(entry.count())).setScale(6,RoundingMode.HALF_UP);
   rows.add(Map.of("label",label,"factor",factor,"count",entry.count(),"subtotal",subtotal));total=total.add(subtotal);
  }
  try{ProcurementRules.quantity(unit,total,true);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
  return new Result(input,unit,List.copyOf(rows),total);
 }
 private static PreviewException bad(String message){return new PreviewException(HttpStatus.BAD_REQUEST,"COUNT_UNIT_INVALID",message);}
}
