package com.example.baget.stocktake;
import com.example.baget.calculation.preview.PreviewException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CountUnitServiceTest {
 private static BigDecimal n(String v){return new BigDecimal(v);}
 @Test void railsAndRemainderUseStoredFactor(){
  var db=mock(JdbcTemplate.class);
  when(db.queryForList("SELECT label,base_unit,factor FROM material_count_units WHERE id=? AND part_no=?",5L,10L)).thenReturn(List.of(Map.of("label","Рейка","base_unit","METER","factor",n("2.9"))));
  var result=new CountUnitService(db).calculate(10,"METER",new CountUnitService.Input(n("1.4"),List.of(new CountUnitService.Entry(5L,10,null,null))));
  assertEquals(0,n("30.4").compareTo(result.total()));assertEquals(n("2.9"),result.entries().get(0).get("factor"));
 }
 @Test void rectangularOffcutsUseMillimetresAndKeepDimensions(){
  var input=new CountUnitService.Input(n("0.1"),List.of(new CountUnitService.Entry(null,2,n("300"),n("400"))));
  var result=new CountUnitService(mock(JdbcTemplate.class)).calculate(1,"SQUARE_METER",input);
  assertEquals(0,n("0.34").compareTo(result.total()));assertSame(input,result.input());
 }
 @Test void otherMaterialOrUnitIsRejected(){
  var service=new CountUnitService(mock(JdbcTemplate.class));
  assertThrows(PreviewException.class,()->service.calculate(2,"METER",new CountUnitService.Input(BigDecimal.ZERO,List.of(new CountUnitService.Entry(5L,1,null,null)))));
  assertThrows(PreviewException.class,()->service.calculate(2,"PIECE",new CountUnitService.Input(n("0.5"),List.of())));
  assertThrows(PreviewException.class,()->service.calculate(2,"METER",new CountUnitService.Input(BigDecimal.ZERO,List.of(new CountUnitService.Entry(null,1,n("300"),n("400"))))));
 }
 @Test void zeroIsAValidCountAndLargeTotalsAreRejected(){
  var service=new CountUnitService(mock(JdbcTemplate.class));
  assertEquals(0,service.calculate(1,"PIECE",new CountUnitService.Input(BigDecimal.ZERO,List.of())).total().signum());
  assertThrows(PreviewException.class,()->service.calculate(1,"SQUARE_METER",new CountUnitService.Input(BigDecimal.ZERO,List.of(new CountUnitService.Entry(null,1000000,n("100000"),n("100000"))))));
 }
}
