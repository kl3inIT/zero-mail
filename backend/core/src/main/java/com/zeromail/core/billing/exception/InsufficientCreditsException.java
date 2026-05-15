package com.zeromail.core.billing.exception;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when the tenant's available balance is less than {@link CallSite#cost()} during ledger
 * reservation.
 *
 * <p><b>Privacy invariant:</b> this exception carries no balance number. The HTTP layer maps it to
 * 402 with {@code code="error.billing.insufficient"} and empty parameters so the frontend localizes
 * without reading a precise balance from the error response.
 */
public class InsufficientCreditsException extends BusinessException {

    public InsufficientCreditsException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.PAYMENT_REQUIRED;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_INSUFFICIENT_CREDITS;
    }

    @Override
    public String logEvent() {
        return "insufficient_credits";
    }

    @Override
    public String title() {
        return "Insufficient credits";
    }

    @Override
    public String detail() {
        return "The current tenant balance is too low for this action.";
    }
}
