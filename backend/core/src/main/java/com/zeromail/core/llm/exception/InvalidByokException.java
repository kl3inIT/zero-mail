package com.zeromail.core.llm.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when BYOK credentials or endpoint policy validation fail.
 *
 * <p><b>Privacy invariant.</b> This exception carries no endpoint URL, API key, upstream response
 * body, or model output content.
 */
public class InvalidByokException extends BusinessException {

    public InvalidByokException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.LLM_BYOK_INVALID;
    }

    @Override
    public String logEvent() {
        return "llm_byok_invalid";
    }

    @Override
    public String title() {
        return "Invalid BYOK credentials";
    }

    @Override
    public String detail() {
        return "The BYOK provider, endpoint, or key could not be validated.";
    }
}
