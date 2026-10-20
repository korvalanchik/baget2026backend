package com.example.baget.orderproduct;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineApprovalTest {
    @Test
    void dateBeforeFiveWorkingDaysRequiresApproval() {
        LocalDate today = LocalDate.of(2026, 10, 19); // Monday

        assertTrue(DeadlineApproval.isRequired(LocalDate.of(2026, 10, 23), today));
        assertFalse(DeadlineApproval.isRequired(LocalDate.of(2026, 10, 26), today));
    }

    @Test
    void weekendDaysAreNotCounted() {
        LocalDate today = LocalDate.of(2026, 10, 23); // Friday

        assertTrue(DeadlineApproval.isRequired(LocalDate.of(2026, 10, 29), today));
        assertFalse(DeadlineApproval.isRequired(LocalDate.of(2026, 10, 30), today));
    }

    @Test
    void missingDueDateDoesNotRequireApproval() {
        assertFalse(DeadlineApproval.isRequired(null, LocalDate.of(2026, 10, 19)));
    }
}
