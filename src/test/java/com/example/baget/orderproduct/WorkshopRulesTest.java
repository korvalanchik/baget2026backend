package com.example.baget.orderproduct;
import org.junit.jupiter.api.Test;
import static com.example.baget.orderproduct.WorkshopRules.State.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkshopRulesTest {
    @Test void receiveBeforeWork() {
        assertTrue(WorkshopRules.transition(WAITING,ACCEPTED));
        assertFalse(WorkshopRules.transition(WAITING,IN_PROGRESS));
        assertFalse(WorkshopRules.editable(WAITING));
        assertTrue(WorkshopRules.editable(ACCEPTED));
    }
    @Test void pausePreservesWorkAndNeedsReason() {
        assertTrue(WorkshopRules.transition(IN_PROGRESS,PAUSED));
        assertTrue(WorkshopRules.transition(PAUSED,IN_PROGRESS));
        assertTrue(WorkshopRules.needsReason(IN_PROGRESS,PAUSED));
        assertTrue(WorkshopRules.needsReason(PAUSED,IN_PROGRESS));
        assertFalse(WorkshopRules.editable(PAUSED));
    }
    @Test void completionMustUsePostingEndpoint() {
        for(var state:WorkshopRules.State.values()) assertFalse(WorkshopRules.transition(state,COMPLETED));
    }
    @Test void confirmedProductCannotReopenOrBecomeManagerEditable() {
        for(var state:WorkshopRules.State.values()) assertFalse(WorkshopRules.transition(COMPLETED,state));
        assertFalse(WorkshopRules.transition(IN_PROGRESS,WAITING));
        assertFalse(WorkshopRules.editable(COMPLETED));
    }
}
