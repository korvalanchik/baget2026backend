package com.example.baget.calculation.suspension;

import java.math.BigDecimal;
import java.util.Objects;
import static com.example.baget.calculation.suspension.SuspensionRules.*;

/** Dependency-free checks, also invoked from JUnit. No -ea flag required. */
public final class SuspensionRulesChecks {
    private static final SuspensionRules RULES = new SuspensionRules();
    private static Plan resolve(String w, boolean dvp, boolean mirror, Mode mode, Long id,
                                Integer count, boolean pozzi, Integer pozziCount) {
        return RULES.resolve(new BigDecimal(w), dvp, mirror, mode, id, count, pozzi, pozziCount);
    }
    private static void auto(String w, boolean dvp, boolean mirror, boolean pozzi,
                             long id, int count, int feet, boolean cord) {
        var actual = resolve(w, dvp, mirror, Mode.AUTO, null, null, pozzi, null);
        equal(new Plan(id, count, feet, cord), actual);
    }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid selection was accepted");
    }
    public static void runAll() {
        auto("149.999", true, false, false, H01, 1, 0, false);
        auto("150", true, false, false, H04, 1, 0, false);
        auto("150.001", true, false, false, H04, 1, 0, false);
        auto("450", true, false, false, H04, 1, 0, false);
        auto("450.001", true, false, false, H04, 2, 0, false);
        auto("299.999", true, false, true, H04, 1, 1, false);
        auto("300", true, false, true, H04, 1, 2, false);
        auto("300.001", true, false, true, H04, 1, 2, false);
        auto("799.999", false, false, false, H099, 2, 0, true);
        auto("800", false, false, false, H099, 2, 0, true);
        auto("800.001", false, false, false, POWER, 2, 0, true);
        auto("399.999", false, true, false, H099, 2, 0, true);
        auto("400", false, true, false, H099, 2, 0, true);
        auto("400.001", false, true, false, POWER, 2, 0, false);
        auto("100", true, true, false, H099, 2, 0, true);
        auto("401", true, true, false, POWER, 2, 0, false);
        equal(new Plan(null, 0, 2, false), resolve("300", true, false, Mode.NONE, null, null, true, null));
        equal(new Plan(null, 0, 0, false), resolve("300", false, false, Mode.NONE, null, null, false, null));
        equal(new Plan(H099, 1, 0, true), resolve("300", true, false, Mode.MANUAL, H099, 1, false, null));
        equal(new Plan(POWER, 1, 0, true), resolve("300", true, false, Mode.MANUAL, POWER, 1, false, null));
        equal(new Plan(H04, 4, 3, false), resolve("300", true, false, Mode.AUTO, null, 4, true, 3));
        equal(new Plan(H099, 1, 0, true), resolve("200", false, true, Mode.AUTO, null, 1, false, null));
        rejects(() -> resolve("200", false, false, Mode.MANUAL, H01, null, false, null));
        rejects(() -> resolve("200", true, true, Mode.MANUAL, H04, null, false, null));
        rejects(() -> resolve("300", true, false, Mode.MANUAL, H099, null, true, null));
        rejects(() -> resolve("300", true, false, Mode.MANUAL, POWER, null, true, null));
        rejects(() -> resolve("300", false, false, Mode.NONE, null, null, true, null));
        rejects(() -> resolve("300", true, false, Mode.AUTO, H04, null, false, null));
        rejects(() -> resolve("300", true, false, Mode.MANUAL, null, null, false, null));
        rejects(() -> resolve("300", true, false, Mode.MANUAL, 999L, null, false, null));
        rejects(() -> resolve("300", true, false, Mode.NONE, null, 2, true, null));
        rejects(() -> resolve("300", true, false, Mode.AUTO, null, 0, false, null));
        rejects(() -> resolve("300", true, false, Mode.AUTO, null, null, false, 1));
        rejects(() -> resolve("801", false, false, Mode.MANUAL, H099, null, false, null));
        rejects(() -> resolve("401", true, true, Mode.MANUAL, H099, null, false, null));
    }
    public static void main(String[] args) {
        runAll();
        System.out.println("35 suspension rule checks passed");
    }
}
