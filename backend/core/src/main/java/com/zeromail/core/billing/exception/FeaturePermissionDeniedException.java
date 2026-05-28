package com.zeromail.core.billing.exception;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown by {@code FeaturePermissionResolver} when the tenant's plan does not enable the requested
 * {@link CallSite}. Mapped to HTTP 402 so the frontend can render an upgrade CTA scoped to the
 * specific feature.
 *
 * <p><b>Privacy invariant:</b> carries only the call site and plan code in structured fields — no
 * balance number, no LLM payload, no user PII. The HTTP layer surfaces these via {@code
 * ProblemDetail.properties} so the FE switches on errorCode + featureCode and localizes without
 * reading server-supplied prose.
 */
public class FeaturePermissionDeniedException extends BusinessException {

    private final CallSite callSite;
    private final String planCode;

    public FeaturePermissionDeniedException(CallSite callSite, String planCode) {
        super();
        this.callSite = callSite;
        this.planCode = planCode;
    }

    public CallSite getCallSite() {
        return callSite;
    }

    public String getPlanCode() {
        return planCode;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.PAYMENT_REQUIRED;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.BILLING_PLAN_FEATURE_DISABLED;
    }

    @Override
    public String logEvent() {
        return "plan_feature_denied";
    }

    @Override
    public String title() {
        return "Plan does not include this feature";
    }

    @Override
    public String detail() {
        return "The tenant's current billing plan does not enable the requested feature.";
    }
}
