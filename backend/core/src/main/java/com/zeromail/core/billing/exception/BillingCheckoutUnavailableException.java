package com.zeromail.core.billing.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class BillingCheckoutUnavailableException extends BusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.SERVICE_UNAVAILABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_CHECKOUT_UNAVAILABLE;
    }

    @Override
    public String logEvent() {
        return "billing_checkout_unavailable";
    }

    @Override
    public String title() {
        return "Checkout unavailable";
    }

    @Override
    public String detail() {
        return "Checkout is not available for the selected billing plan.";
    }
}
