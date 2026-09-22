package com.example.baget.calculation;

import com.example.baget.parts.Parts;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class MaterialConsumptionService {
    private final ConsumptionCalculator calculator = new ConsumptionCalculator();

    public ConsumptionResult calculate(Parts part, CalculationContext context) {
        if (part == null || part.getPartKind() == null) {
            throw new IllegalArgumentException("Part is missing or not configured");
        }
        Double legacyWidth = part.getProfilWidth();
        if (legacyWidth != null && !Double.isFinite(legacyWidth)) {
            throw new IllegalArgumentException("Invalid profile width for part " + part.getPartNo());
        }
        BigDecimal widthMeters = legacyWidth == null ? null : BigDecimal.valueOf(legacyWidth);
        return calculator.calculate(part.getCalculationMethod(), part.getUnitType(),
                widthMeters, part.getCalculationParams(), context);
    }
}
