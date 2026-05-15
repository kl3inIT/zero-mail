package com.zeromail.core.billing.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when a forbidden ledger state transition is attempted, such as releasing a settled
 * reservation or settling a released reservation.
 *
 * <p><b>HTTP mapping (D-D4):</b> this maps to HTTP 500 with {@code
 * code="error.billing.ledger.invalidState"}. This is a programming-error class, not a
 * user-recoverable condition.
 */
public class IllegalLedgerStateException extends BusinessException {

    public IllegalLedgerStateException(String message) {
        super(message);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.INTERNAL;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_LEDGER_INVALID_STATE;
    }

    @Override
    public String logEvent() {
        return "illegal_ledger_state";
    }

    @Override
    public String title() {
        return "Ledger state invariant violated";
    }

    @Override
    public String detail() {
        return "An internal billing-state transition was attempted in an invalid order.";
    }
}
