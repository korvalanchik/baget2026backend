package com.example.baget.prro.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroSubmissionOrderGuardTest {

    @Test
    void blocksNewerLocalNumberWhileOlderOperationIsPrepared() {
        var newer = operation(12L, 12, PrroOperationService.Status.SIGNED);

        var error = assertThrows(PrroOperationService.SubmissionOrderConflictException.class,
                () -> PrroOperationService.validateSubmissionOrder(newer, List.of(
                        pending(11L, 11, PrroOperationService.Status.PREPARED),
                        pending(12L, 12, PrroOperationService.Status.SIGNED))));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("Earlier PRRO local number 11"));
    }

    @Test
    void blocksAnyNewSubmissionWhileEarlierRequestOutcomeIsUnknown() {
        var candidate = operation(13L, 13, PrroOperationService.Status.SIGNED);

        var error = assertThrows(PrroOperationService.SubmissionOrderConflictException.class,
                () -> PrroOperationService.validateSubmissionOrder(candidate, List.of(
                        pending(12L, 12, PrroOperationService.Status.UNKNOWN_RESULT),
                        pending(13L, 13, PrroOperationService.Status.SIGNED))));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("unresolved submission 12"));
    }

    @Test
    void blocksConcurrentSendInProgressEvenWhenItsLocalNumberIsHigher() {
        var candidate = operation(12L, 12, PrroOperationService.Status.SIGNED);

        assertThrows(PrroOperationService.SubmissionOrderConflictException.class,
                () -> PrroOperationService.validateSubmissionOrder(candidate, List.of(
                        pending(12L, 12, PrroOperationService.Status.SIGNED),
                        pending(13L, 13, PrroOperationService.Status.SENDING))));
    }

    @Test
    void permitsLowestPendingOperationWhenOnlyLaterOperationIsPrepared() {
        var candidate = operation(12L, 12, PrroOperationService.Status.SIGNED);

        assertDoesNotThrow(() -> PrroOperationService.validateSubmissionOrder(candidate, List.of(
                pending(12L, 12, PrroOperationService.Status.SIGNED),
                pending(13L, 13, PrroOperationService.Status.PREPARED))));
    }

    @Test
    void ignoresCandidateWhenItAppearsInPendingRows() {
        var candidate = operation(12L, 12, PrroOperationService.Status.SIGNED);

        assertDoesNotThrow(() -> PrroOperationService.validateSubmissionOrder(candidate, List.of(
                pending(12L, 12, PrroOperationService.Status.SIGNED))));
    }

    private static PrroOperationService.Operation operation(
            long id, int localNumber, PrroOperationService.Status status) {
        return new PrroOperationService.Operation(
                id, 7L, "shift-1", "idempotency-" + id, "a".repeat(64),
                "PAYMENT", "order-" + id, "CHK", localNumber, "operator-key-1",
                status, "b".repeat(64), null, null, null, "cashier");
    }

    private static PrroOperationService.PendingSubmission pending(
            long id, int localNumber, PrroOperationService.Status status) {
        return new PrroOperationService.PendingSubmission(id, localNumber, status);
    }
}
