package com.example.baget.calculation.preview;

import com.example.baget.calculation.CalculationContext;
import com.example.baget.calculation.ConsumptionResult;
import com.example.baget.calculation.MaterialConsumptionService;
import com.example.baget.parts.CalculationMethod;
import com.example.baget.parts.PartsRepository;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MaterialPreviewService {
    private final PartsRepository parts;
    private final MaterialConsumptionService consumption;
    private final PartPriceResolver prices;
    private final Validator validator;

    public MaterialPreviewService(PartsRepository parts, MaterialConsumptionService consumption,
                                  PartPriceResolver prices, Validator validator) {
        this.parts = parts;
        this.consumption = consumption;
        this.prices = prices;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public MaterialPreviewResponse preview(MaterialPreviewRequest request, Authentication auth) {
        prices.requireAuthenticated(auth);
        // Also validate callers outside HTTP (debugger, other services).
        if (request == null || !validator.validate(request).isEmpty()) {
            throw badRequest("Некоректні параметри запиту");
        }
        var part = parts.findById(request.partNo()).orElseThrow(() ->
                new PreviewException(HttpStatus.NOT_FOUND, "PART_NOT_FOUND", "Матеріал не знайдено"));
        var method = part.getCalculationMethod();
        if (method == null || part.getUnitType() == null || part.getPartKind() == null
                || part.getCalculationParams() == null) {
            throw new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "PART_NOT_CONFIGURED",
                    "Матеріал ще не налаштований для нового калькулятора");
        }
        switch (method) {
            case AREA, AREA_WITH_MARGIN, PERIMETER, FRAME_PROFILE -> {
                if (request.widthMm() == null || request.heightMm() == null) {
                    throw badRequest("Для цього алгоритму потрібні widthMm і heightMm");
                }
            }
            case WIDTH_PLUS_ALLOWANCE -> {
                if (request.widthMm() == null) throw badRequest("Потрібне поле widthMm");
            }
            case QUANTITY, FIXED -> { }
        }
        int units = request.unitsPerProduct() == null ? 1 : request.unitsPerProduct();
        if (method != CalculationMethod.QUANTITY && units != 1) {
            throw badRequest("unitsPerProduct відмінне від 1 дозволено лише для QUANTITY");
        }
        var context = new CalculationContext(request.widthMm(), request.heightMm(),
                request.productQuantity(), BigDecimal.valueOf(units));
        ConsumptionResult result;
        try {
            result = consumption.calculate(part, context);
        } catch (IllegalArgumentException ex) {
            throw new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CALCULATION_SETTINGS",
                    "Некоректні налаштування матеріалу: " + ex.getMessage());
        }
        BigDecimal price = prices.resolve(part, auth);
        // Round once per material line, after multiplying all consumption by the price.
        BigDecimal amount = result.totalConsumption().multiply(price).setScale(2, RoundingMode.HALF_UP);
        return new MaterialPreviewResponse(part.getPartNo(), part.getDescription(), method,
                result.unit(), result.consumptionPerProduct(), result.productQuantity(),
                result.totalConsumption(), price, amount, "UAH");
    }

    private static PreviewException badRequest(String message) {
        return new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
