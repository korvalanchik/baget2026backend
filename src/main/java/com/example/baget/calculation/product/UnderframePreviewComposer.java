package com.example.baget.calculation.product;

import com.example.baget.calculation.*;
import com.example.baget.calculation.preview.*;
import com.example.baget.parts.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Internal composition, invoked within ProductPreviewService's read-only transaction. */
@Component
public class UnderframePreviewComposer {
    private final PartsRepository parts;
    private final MaterialPreviewService materials;
    private final PartPriceResolver prices;
    private final UnderframeCrossbarService crossbars;

    public UnderframePreviewComposer(PartsRepository parts, MaterialPreviewService materials,
                                     PartPriceResolver prices, UnderframeCrossbarService crossbars) {
        this.parts = parts;
        this.materials = materials;
        this.prices = prices;
        this.crossbars = crossbars;
    }

    public List<ProductPreviewResponse.Line> compose(ProductPreviewRequest request, Authentication auth) {
        prices.requireAuthenticated(auth);
        var underframe = parts.findById(request.underframePartNo()).orElseThrow(() ->
                error("COMPOSITION_PART_MISSING", "Підрамник не знайдено"));
        UnderframeCrossbarService.CrossbarUsage usage;
        try {
            usage = crossbars.calculate(underframe, new CalculationContext(request.widthMm(), request.heightMm(),
                    request.productQuantity(), BigDecimal.ONE));
        } catch (IllegalArgumentException ex) {
            throw error("INVALID_UNDERFRAME_GEOMETRY_OR_SETTINGS", ex.getMessage());
        }
        if (underframe.getPartNo().equals(usage.partNo())) {
            throw error("INVALID_CROSSBAR_BINDING", "Підрамник і рейка перемички повинні бути різними позиціями");
        }
        var main = materials.preview(new MaterialPreviewRequest(request.underframePartNo(), request.widthMm(),
                request.heightMm(), request.productQuantity(), 1), auth);
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        lines.add(new ProductPreviewResponse.Line("UNDERFRAME", "SELECTED", main));

        // A small underframe neither needs a rail price nor includes a zero-value line.
        if (usage.consumption().segmentsPerProduct().isEmpty()) return List.copyOf(lines);

        var rail = parts.findById(usage.partNo()).orElseThrow(() ->
                error("COMPOSITION_PART_MISSING", "Не знайдено рейку перемичок " + usage.partNo()));
        // Transitional adapter: V47 intentionally leaves rail 28030 unconfigured.
        // Trust the server-side binding, require legacy length unit, reject conflicting metadata.
        if (rail.getInQuality() == null || Double.compare(rail.getInQuality(), 2.0) != 0
                || (rail.getUnitType() != null && rail.getUnitType() != UnitType.METER)
                || (rail.getPartKind() != null && rail.getPartKind() != PartKind.MATERIAL)
                || rail.getCalculationMethod() != null
                || (rail.getCalculationParams() != null && !rail.getCalculationParams().isEmpty())) {
            throw error("INVALID_CROSSBAR_SETTINGS", "Налаштування рейки суперечать розрахунку довжини правилом");
        }
        var result = usage.consumption();
        BigDecimal price = prices.resolve(rail, auth);
        BigDecimal amount = result.totalConsumption().multiply(price).setScale(2, RoundingMode.HALF_UP);
        // No material-level formula: the length was calculated by the composition rule.
        var calculation = new MaterialPreviewResponse(rail.getPartNo(), rail.getDescription(), null,
                UnitType.METER, result.consumptionPerProduct(), result.productQuantity(),
                result.totalConsumption(), price, amount, "UAH");
        lines.add(new ProductPreviewResponse.Line("CROSSBAR", "UNDERFRAME_CROSSBAR_RULE",
                calculation, result.segmentsPerProduct()));
        return List.copyOf(lines);
    }

    private static PreviewException error(String code, String message) {
        return new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
