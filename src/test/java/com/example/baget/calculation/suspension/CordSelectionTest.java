package com.example.baget.calculation.suspension;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CordSelectionTest {
    private final SuspensionRules rules = new SuspensionRules();
    private SuspensionRules.Plan auto(String width, boolean backing, boolean mirror) {
        return rules.resolve(new BigDecimal(width), backing, mirror,
                SuspensionRules.Mode.AUTO, null, null, false, null);
    }
    @Test void omittedChoicePreservesAutomaticBehavior() {
        var plan = auto("400", false, true);
        assertSame(plan, rules.withCord(plan, null));
        assertTrue(plan.cordIncluded());
    }
    @Test void exclusionKeepsHangersAndQuantities() {
        var automatic = auto("300", false, false);
        var result = rules.withCord(automatic, false);
        assertFalse(result.cordIncluded());
        assertEquals(automatic.hangerPartNo(), result.hangerPartNo());
        assertEquals(automatic.hangersPerProduct(), result.hangersPerProduct());
        assertEquals(automatic.pozziPerProduct(), result.pozziPerProduct());
    }
    @Test void inclusionRespectsExistingCompatibilityRules() {
        assertTrue(rules.withCord(auto("400", false, true), true).cordIncluded());
        assertTrue(rules.withCord(auto("801", false, false), true).cordIncluded());
        assertThrows(IllegalArgumentException.class, () -> rules.withCord(auto("400.001", true, true), true));
        assertThrows(IllegalArgumentException.class, () -> rules.withCord(auto("300", true, false), true));
        var none = rules.resolve(new BigDecimal("300"), false, false,
                SuspensionRules.Mode.NONE, null, null, false, null);
        assertThrows(IllegalArgumentException.class, () -> rules.withCord(none, true));
        assertFalse(rules.withCord(none, false).cordIncluded());
    }
}
