package com.example.baget.calculation;

import com.example.baget.parts.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class UnderframeCrossbarService {
    public record CrossbarUsage(long partNo, CrossbarCalculator.Result consumption) {}
    private final long crossbarPartNo;
    private final CrossbarCalculator calculator = new CrossbarCalculator();

    public UnderframeCrossbarService(@Value("${baget.calculation.crossbar-part-no:28030}") long crossbarPartNo) {
        if (crossbarPartNo <= 0) throw new IllegalArgumentException("crossbarPartNo must be positive");
        this.crossbarPartNo = crossbarPartNo;
    }

    /** No reads/writes to the database; the caller provides a loaded underframe. */
    public CrossbarUsage calculate(Parts underframe, CalculationContext context) {
        if (underframe == null || context == null
                || underframe.getPartKind() != PartKind.MATERIAL
                || underframe.getUnitType() != UnitType.METER
                || underframe.getCalculationMethod() != CalculationMethod.PERIMETER
                || underframe.getCalculationParams() == null
                || !underframe.getCalculationParams().keySet().equals(Set.of("railWidthMm"))) {
            throw new IllegalArgumentException("Underframe must be configured with railWidthMm");
        }
        return new CrossbarUsage(crossbarPartNo, calculator.calculate(context.widthMm(), context.heightMm(),
                underframe.getCalculationParams().get("railWidthMm"), context.productQuantity()));
    }
}
