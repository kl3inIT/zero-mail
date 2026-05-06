package com.zeromail.core.billing.model;

/**
 * Thrown when a forbidden ledger state transition is attempted, such as releasing a settled
 * reservation or settling a released reservation.
 *
 * <p><b>HTTP mapping (D-D4):</b> this maps to HTTP 500 with
 * {@code code="error.billing.ledger.invalidState"}. This is a programming-error class,
 * not a user-recoverable condition.
 */
public class IllegalLedgerStateException extends RuntimeException {

    public IllegalLedgerStateException(String message) {
        super(message);
    }
}
