package com.zeromail.core.billing.model;

/**
 * Thrown by {@link CreditLedger#reserve(java.util.UUID, CallSite)} when the tenant's
 * available balance is less than {@link CallSite#cost()}.
 *
 * <p><b>Privacy invariant:</b> this exception carries no balance number. The HTTP layer maps
 * it to 402 with {@code code="error.billing.insufficient"} and empty parameters so the
 * frontend localizes without reading a precise balance from the error response.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException() {
        super();
    }
}
