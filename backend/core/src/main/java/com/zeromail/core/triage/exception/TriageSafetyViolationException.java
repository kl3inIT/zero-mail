package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Privacy-safe safety rejection signal. Carries no rejected action name, message content, prompt,
 * completion, sender address, or model output.
 */
public class TriageSafetyViolationException extends BusinessException {

    public TriageSafetyViolationException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.INTERNAL;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.TRIAGE_SAFETY_VIOLATION;
    }

    @Override
    public String logEvent() {
        return "triage_safety_violation";
    }

    @Override
    public String title() {
        return "Triage safety violation";
    }

    @Override
    public String detail() {
        return "The triage action violated the safety policy.";
    }
}
