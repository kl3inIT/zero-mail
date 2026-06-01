package com.zeromail.core.billing.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class BillingPlanDowngradeNotAllowedException extends BusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_PLAN_DOWNGRADE_NOT_ALLOWED;
    }

    @Override
    public String logEvent() {
        return "billing_plan_downgrade_not_allowed";
    }

    @Override
    public String title() {
        return "Plan downgrade is not allowed";
    }

    @Override
    public String detail() {
        return "The selected plan is lower than the tenant's active paid plan.";
    }
}
