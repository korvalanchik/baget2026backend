package com.example.baget.calculation.mounting;

import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
import com.example.baget.parts.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import static com.example.baget.calculation.mounting.MountingPreviewRequest.MountingType.*;

@Service
public class MountingPreviewService {
    private final PartsRepository parts;
    private final MaterialPreviewService materials;
    private final PartPriceResolver prices;
    private final UnderframePreviewComposer underframes;
    private final Validator validator;

    public MountingPreviewService(PartsRepository parts, MaterialPreviewService materials,
            PartPriceResolver prices, UnderframePreviewComposer underframes, Validator validator) {
        this.parts = parts; this.materials = materials; this.prices = prices;
        this.underframes = underframes; this.validator = validator;
    }

    @Transactional(readOnly = true)
    public MountingPreviewResponse preview(MountingPreviewRequest r, Authentication auth) {
        prices.requireAuthenticated(auth);
        if (r == null || !validator.validate(r).isEmpty())
            throw new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Перевірте параметри натяжки");
        try { MountingRules.validate(r); }
        catch (IllegalArgumentException ex) {
            throw new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_MOUNTING_SELECTION", ex.getMessage());
        }
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        if (r.mountingType() == PLANE) {
            configured(5130L, PartKind.MATERIAL, CalculationMethod.AREA_WITH_MARGIN, UnitType.SQUARE_METER,
                    Map.of("marginWidthMm", BigDecimal.TEN, "marginHeightMm", BigDecimal.TEN));
            configured(33370L, PartKind.WORK, CalculationMethod.FIXED, UnitType.PIECE, Map.of());
            lines.add(materialLine(5130L, "STRETCH_MATERIAL", "PLANE_RULE", r, auth));
            lines.add(materialLine(33370L, "STRETCH_WORK", "PLANE_RULE", r, auth));
        }
        if (r.mountingType() == UNDERFRAME) {
            lines.addAll(underframes.compose(new ProductPreviewRequest(
                    ProductPreviewRequest.ProductType.UNDERFRAME, r.widthMm(), r.heightMm(),
                    r.productQuantity(), null, null, r.underframePartNo()), auth));
            long work = MountingRules.workPartNo(r.canvasType());
            configured(work, PartKind.WORK, CalculationMethod.PERIMETER, UnitType.METER, Map.of());
            lines.add(materialLine(work, "STRETCH_WORK", "UNDERFRAME_STRETCH_RULE", r, auth));
        }
        if (r.canvasPartNo() != null) {
            // Confirmed canvas binding. Do not accept every AREA part (e.g. mirror or glass) as canvas.
            if (r.canvasPartNo() != 27970L)
                throw new PreviewException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CANVAS_PART",
                        "Для полотна наразі налаштована позиція 27970");
            // AREA remains the material default. A usage rule adds the margin without mutating Parts.
            var canvas = configured(r.canvasPartNo(), PartKind.MATERIAL, CalculationMethod.AREA,
                    UnitType.SQUARE_METER, Map.of());
            BigDecimal perProduct = MountingRules.canvasArea(r);
            BigDecimal total = perProduct.multiply(BigDecimal.valueOf(r.productQuantity()));
            BigDecimal price = prices.resolve(canvas, auth);
            var result = new MaterialPreviewResponse(canvas.getPartNo(), canvas.getDescription(),
                    r.mountingType() == UNDERFRAME ? CalculationMethod.AREA_WITH_MARGIN : CalculationMethod.AREA,
                    UnitType.SQUARE_METER, perProduct, r.productQuantity(), total, price,
                    total.multiply(price).setScale(2, RoundingMode.HALF_UP), "UAH");
            lines.add(new ProductPreviewResponse.Line("CANVAS", "CANVAS_USAGE_RULE", result));
        }
        var totals = ProductTotals.fromUnroundedAmounts(lines.stream()
                .map(line -> line.calculation().totalConsumption().multiply(line.calculation().unitPrice())).toList());
        return new MountingPreviewResponse(r.mountingType(), r.canvasType(), r.productQuantity(), lines,
                totals.linesSubtotal(), totals.roundingAdjustment(), totals.total(), "UAH");
    }

    private ProductPreviewResponse.Line materialLine(long id, String role, String source,
            MountingPreviewRequest r, Authentication auth) {
        return new ProductPreviewResponse.Line(role, source, materials.preview(new MaterialPreviewRequest(
                id, r.widthMm(), r.heightMm(), r.productQuantity(), 1), auth));
    }

    private Parts configured(long id, PartKind kind, CalculationMethod method, UnitType unit,
            Map<String, BigDecimal> expected) {
        var part = parts.findById(id).orElseThrow(() -> error("COMPOSITION_PART_MISSING", "Не знайдено позицію " + id));
        var params = part.getCalculationParams();
        boolean matches = params != null && params.keySet().equals(expected.keySet())
                && expected.entrySet().stream().allMatch(e -> params.get(e.getKey()) != null
                && params.get(e.getKey()).compareTo(e.getValue()) == 0);
        if (part.getPartKind() != kind || part.getCalculationMethod() != method || part.getUnitType() != unit || !matches)
            throw error("INVALID_MOUNTING_SETTINGS", "Перевірте налаштування позиції " + id);
        return part;
    }

    private static PreviewException error(String code, String message) {
        return new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
