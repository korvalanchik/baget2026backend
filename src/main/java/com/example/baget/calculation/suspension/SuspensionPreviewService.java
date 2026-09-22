package com.example.baget.calculation.suspension;

import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.product.*;
import com.example.baget.parts.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;

@Service
public class SuspensionPreviewService {
    private final PartsRepository parts;
    private final MaterialPreviewService materials;
    private final PartPriceResolver prices;
    private final Validator validator;
    private final SuspensionRules rules = new SuspensionRules();

    public SuspensionPreviewService(PartsRepository parts, MaterialPreviewService materials,
                                    PartPriceResolver prices, Validator validator) {
        this.parts = parts; this.materials = materials; this.prices = prices; this.validator = validator;
    }

    @Transactional(readOnly = true)
    public SuspensionPreviewResponse preview(SuspensionPreviewRequest r, Authentication auth) {
        prices.requireAuthenticated(auth);
        if (r == null || !validator.validate(r).isEmpty())
            throw bad("INVALID_REQUEST", "Перевірте поля кріплення");
        if ((r.backingPartNo() != null && !SuspensionRules.isRigidBacking(r.backingPartNo()))
                || (r.mirrorPartNo() != null && r.mirrorPartNo() != SuspensionRules.MIRROR))
            throw bad("UNSUPPORTED_SUSPENSION_CONTEXT", "Підтримуються ДВП 4460, ПВХ 27520 та дзеркало 27760");
        SuspensionRules.Plan plan;
        try {
            plan = rules.resolve(r.widthMm(), r.backingPartNo() != null, r.mirrorPartNo() != null,
                    r.hangerMode(), r.hangerPartNo(), r.hangersPerProduct(), r.includePozzi(), r.pozziPerProduct());
            plan = rules.withCord(plan, r.includeCord());
        } catch (IllegalArgumentException ex) {
            throw bad("INVALID_SUSPENSION_SELECTION", ex.getMessage());
        }
        // Context is not a billable line in this endpoint. Check that supplied references exist.
        if (r.backingPartNo() != null) load(r.backingPartNo());
        if (r.mirrorPartNo() != null) load(r.mirrorPartNo());
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        if (plan.hangerPartNo() != null)
            lines.add(line(plan.hangerPartNo(), "HANGER", plan.hangersPerProduct(), false, r, auth));
        if (plan.pozziPerProduct() > 0)
            lines.add(line(SuspensionRules.POZZI, "POZZI", plan.pozziPerProduct(), false, r, auth));
        if (plan.cordIncluded())
            lines.add(line(SuspensionRules.CORD, "CORD", 1, true, r, auth));
        var totals = ProductTotals.fromUnroundedAmounts(lines.stream()
                .map(l -> l.calculation().totalConsumption().multiply(l.calculation().unitPrice())).toList());
        return new SuspensionPreviewResponse(plan, r.productQuantity(), lines, totals.linesSubtotal(),
                totals.roundingAdjustment(), totals.total(), "UAH");
    }

    private ProductPreviewResponse.Line line(long id, String role, int quantity, boolean cord,
                                             SuspensionPreviewRequest r, Authentication auth) {
        var p = load(id);
        var params = p.getCalculationParams();
        boolean validParams = params != null && (cord
                ? params.keySet().equals(java.util.Set.of("allowanceMm")) && params.get("allowanceMm") != null
                    && params.get("allowanceMm").compareTo(BigDecimal.valueOf(100)) == 0
                : params.isEmpty());
        if (p.getPartKind() != PartKind.MATERIAL
                || p.getUnitType() != (cord ? UnitType.METER : UnitType.PIECE)
                || p.getCalculationMethod() != (cord ? CalculationMethod.WIDTH_PLUS_ALLOWANCE : CalculationMethod.QUANTITY)
                || !validParams)
            throw new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SUSPENSION_SETTINGS",
                    "Перевірте налаштування позиції " + id);
        var result = materials.preview(new MaterialPreviewRequest(id, r.widthMm(), null, r.productQuantity(), quantity), auth);
        return new ProductPreviewResponse.Line(role, "SUSPENSION_RULE", result);
    }

    private Parts load(long id) {
        return parts.findById(id).orElseThrow(() -> new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY,
                "COMPOSITION_PART_MISSING", "Не знайдено позицію " + id));
    }
    private static PreviewException bad(String code, String message) {
        return new PreviewException(HttpStatus.BAD_REQUEST, code, message);
    }
}
