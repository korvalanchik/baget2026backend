package com.example.baget.orderproduct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
/** Allocate the already-calculated rounding difference without introducing negative invoice lines. */
public final class AmountAllocation {
    private AmountAllocation() {}
    public static List<BigDecimal> reconcile(List<BigDecimal> lines, BigDecimal total) {
        if(total.signum()<0 || lines.stream().anyMatch(v->v.signum()<0)) throw new IllegalArgumentException("Negative price");
        var amounts=new ArrayList<>(lines);
        var delta=total.subtract(lines.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        if(amounts.isEmpty()) {if(total.signum()!=0)throw new IllegalArgumentException("No lines");return amounts;}
        if(delta.signum()>=0) amounts.set(0,amounts.get(0).add(delta));
        else {
            var remaining=delta.negate();
            for(int i=0;i<amounts.size() && remaining.signum()>0;i++) {
                var reduction=remaining.min(amounts.get(i));
                amounts.set(i,amounts.get(i).subtract(reduction));remaining=remaining.subtract(reduction);
            }
            if(remaining.signum()>0)throw new IllegalArgumentException("Rounding exceeds total");
        }
        return List.copyOf(amounts);
    }
}
