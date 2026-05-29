package com.zeromail.core.billing.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class BillingPlanNotFoundException extends BusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_PLAN_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "billing_plan_not_found";
    }

    @Override
    public String title() {
        return "Billing plan not found";
    }

    @Override
    public String detail() {
        return "The selected billing plan does not exist or is inactive.";
    }
}
