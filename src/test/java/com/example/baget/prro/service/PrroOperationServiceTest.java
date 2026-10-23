package com.example.baget.prro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PrroOperationServiceTest {

    private JdbcTemplate jdbcTemplate;
    private PrroOperationService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new PrroOperationService(jdbcTemplate);
    }

    @Test
    void rejectsRequestHashThatIsNotSha256BeforeAccessingDatabase() {
        var command = new PrroOperationService.PrepareOperation(
                1L, "shift-1", "payment-42", "not-a-hash",
                "PAYMENT", "42", "CHK", "cashier");

        assertThrows(IllegalArgumentException.class, () -> service.prepare(command));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsPaymentUsingNonReceiptDocumentTypeBeforeAccessingDatabase() {
        var command = new PrroOperationService.PrepareOperation(
                1L, "shift-1", "payment-42", "a".repeat(64),
                "PAYMENT", "42", "ZREPORT", "cashier");

        assertThrows(IllegalArgumentException.class, () -> service.prepare(command));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void permitsOnlyTheSignerBoundToTheActiveShift() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> PrroOperationService.validateActiveSignerKey("operator-key-1", "operator-key-1"));

        assertThrows(IllegalStateException.class,
                () -> PrroOperationService.validateActiveSignerKey("operator-key-1", "operator-key-2"));
        assertThrows(IllegalStateException.class,
                () -> PrroOperationService.validateActiveSignerKey(null, "operator-key-1"));
    }

    @Test
    void rejectsIllegalStateTransitionBeforeAccessingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(10L,
                        PrroOperationService.Status.PREPARED,
                        PrroOperationService.Status.FISCALIZED,
                        null, "fiscal-id", 1, null));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsMalformedSignedDocumentHashBeforeAccessingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(10L,
                        PrroOperationService.Status.SENDING,
                        PrroOperationService.Status.FISCALIZED,
                        "not-sha256", "fiscal-id", 1, null));

        verifyNoInteractions(jdbcTemplate);
    }
}
