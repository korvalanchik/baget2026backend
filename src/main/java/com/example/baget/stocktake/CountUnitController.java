package com.example.baget.stocktake;
import com.example.baget.warehouse.WarehouseService;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.procurement.ProcurementRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/warehouse/materials/{part}/count-units")
@Transactional
public class CountUnitController {
 private final JdbcTemplate db;private final WarehouseService warehouse;private final CountUnitService service;
 public CountUnitController(JdbcTemplate db,WarehouseService warehouse,CountUnitService service){this.db=db;this.warehouse=warehouse;this.service=service;}
 public record Preset(@NotBlank @Size(max=100) String label,@NotNull @Positive @DecimalMax("999999999") @Digits(integer=9,fraction=6) BigDecimal factor) {}
 @GetMapping public Object read(@PathVariable("part") long part,Authentication a){
  var material=warehouse.material(part,a);
  if(!Set.of("METER","SQUARE_METER","PIECE").contains(Objects.toString(material.get("unit"),"")))throw new PreviewException(HttpStatus.BAD_REQUEST,"COUNT_UNIT_INVALID","Спочатку налаштуйте складську одиницю матеріалу");
  return Map.of("unit",material.get("unit"),"presets",db.queryForList("SELECT id,label,factor,active FROM material_count_units WHERE part_no=? AND base_unit=? ORDER BY active DESC,label,id DESC",part,material.get("unit")));
 }
 @PostMapping public Object save(@PathVariable("part") long part,@Valid @RequestBody Preset r,Authentication a){
  warehouse.material(part,a);
  String unit=db.queryForObject("SELECT unit_type FROM parts WHERE PartNo=? FOR UPDATE",String.class,part);
  try{ProcurementRules.quantity(unit,r.factor(),false);}catch(IllegalArgumentException e){throw new PreviewException(HttpStatus.BAD_REQUEST,"COUNT_UNIT_INVALID",e.getMessage());}
  String label=r.label().trim();
  var current=db.queryForList("SELECT factor FROM material_count_units WHERE part_no=? AND label=? AND base_unit=? AND active=1",part,label,unit);
  if(current.size()==1 && new BigDecimal(current.get(0).get("factor").toString()).compareTo(r.factor())==0)return read(part,a);
  db.update("UPDATE material_count_units SET active=0 WHERE part_no=? AND label=?",part,label);
  db.update("INSERT INTO material_count_units(part_no,label,base_unit,factor,actor) VALUES(?,?,?,?,?)",part,label,unit,r.factor(),a.getName());
  return read(part,a);
 }
 @PostMapping("/preview") public Object preview(@PathVariable("part") long part,@Valid @RequestBody CountUnitService.Input input,Authentication a){
  var material=warehouse.material(part,a);return service.calculate(part,material.get("unit").toString(),input);
 }
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Матеріал не знайдено"));}
 @ExceptionHandler(PreviewException.class) public ResponseEntity<?> error(PreviewException e){return ResponseEntity.status(e.getStatus()).body(Map.of("message",e.getMessage(),"code",e.getCode()));}
}
