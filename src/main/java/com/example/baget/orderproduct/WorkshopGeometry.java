package com.example.baget.orderproduct;

import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.UnitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Dimensions used by the existing consumption formulas; not an optimizer for sheet/bar cutting. */
public final class WorkshopGeometry {
    private WorkshopGeometry() {}
    public record Size(BigDecimal widthMm,BigDecimal heightMm,String note) {}
    public static Size size(CalculationMethod method,UnitType unit,BigDecimal width,BigDecimal height,
                            Map<String,BigDecimal> params,BigDecimal profileMeters) {
        if(unit==UnitType.PIECE)return new Size(null,null,"Штучна позиція: розміри не застосовуються");
        if(method==CalculationMethod.WIDTH_PLUS_ALLOWANCE)
            return new Size(null,null,"Довжина на екземпляр: "+width.add(params.get("allowanceMm")).stripTrailingZeros().toPlainString()+" мм");
        if(method==null)return new Size(null,null,"Довжини перемичок наведено окремо");
        var w=width.setScale(0,RoundingMode.CEILING);var h=height.setScale(0,RoundingMode.CEILING);
        if(method==CalculationMethod.AREA_WITH_MARGIN)
            return new Size(w.add(params.get("marginWidthMm")),h.add(params.get("marginHeightMm")),"Розмір заготовки з припусками за налаштуванням матеріалу");
        if(method==CalculationMethod.FRAME_PROFILE) {
            var twiceProfile=profileMeters.multiply(new BigDecimal("1000")).setScale(0,RoundingMode.HALF_UP).multiply(new BigDecimal("2"));
            return new Size(w,h,"Розмір оформлення; розрахунок рами на екземпляр: 2 × "+w.add(twiceProfile).toPlainString()+" мм і 2 × "+h.add(twiceProfile).toPlainString()+" мм (з урахуванням ширини профілю)");
        }
        return new Size(w,h,unit==UnitType.SQUARE_METER?"Розмір листової заготовки на один шар":"Розміри, за якими обчислено периметр");
    }
    public static Size canvas(BigDecimal width,BigDecimal height,boolean underframe,boolean print) {
        var margin=BigDecimal.valueOf(underframe?(print?100:80):0);
        return new Size(width.setScale(0,RoundingMode.CEILING).add(margin),height.setScale(0,RoundingMode.CEILING).add(margin),"Розмір полотна з припуском на натяжку");
    }
}
