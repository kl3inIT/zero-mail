package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/** Privacy-safe signal for malformed reply-threading headers. */
public class ThreadingHeaderInvalidException extends BusinessException {

    public ThreadingHeaderInvalidException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.UNPROCESSABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.DRAFT_GENERATION_FAILED;
    }

    @Override
    public String logEvent() {
        return "draft_generation_failed";
    }

    @Override
    public String title() {
        return "Draft generation failed";
    }

    @Override
    public String detail() {
        return "A draft could not be generated for this thread.";
    }
}
