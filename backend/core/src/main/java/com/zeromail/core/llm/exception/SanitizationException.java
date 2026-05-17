package com.zeromail.core.llm.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when a sanitization pipeline step fails. Aborts the gateway call.
 *
 * <p><b>Privacy invariant:</b> this exception carries no email content, no prompt bytes, no
 * completion bytes. Only the failing step name and the underlying cause are retained.
 */
public class SanitizationException extends BusinessException {

    private final String stepName;

    public SanitizationException(String stepName, Throwable cause) {
        super(null, cause);
        this.stepName = stepName;
    }

    public String stepName() {
        return stepName;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.INTERNAL;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.LLM_SANITIZATION_FAILED;
    }

    @Override
    public String logEvent() {
        return "llm_sanitization_failed";
    }

    @Override
    public String title() {
        return "LLM sanitization failed";
    }

    @Override
    public String detail() {
        return "The LLM request could not be sanitized safely.";
    }
}
