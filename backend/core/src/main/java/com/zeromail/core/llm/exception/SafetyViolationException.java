package com.zeromail.core.llm.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when the LLM gateway rejects an action outside the allow-list {@code {LABEL, ARCHIVE,
 * SAVE_DRAFT}}.
 *
 * <p><b>Privacy invariant.</b> This exception carries NO rejected action name, NO tool-call
 * arguments, NO model output content. The HTTP layer maps it to 422 with {@code
 * code="error.llm.safety_violation"}; the frontend localizes without ever seeing the rejected
 * payload.
 *
 * <p><b>Defense-in-depth pairing.</b> Layer 1 enforcement (Spring AI {@code toolChoice="required"}
 * + {@code internalToolExecutionEnabled(false)}) is at the wire level; this exception is the Layer
 * 2 fail-closed signal that the validator caught a function name outside the allow-list. Both
 * layers must independently fail open for an unsafe action to leak.
 */
public class SafetyViolationException extends BusinessException {

    public SafetyViolationException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.UNPROCESSABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.LLM_SAFETY_VIOLATION;
    }

    @Override
    public String logEvent() {
        return "llm_safety_violation";
    }

    @Override
    public String title() {
        return "LLM safety violation";
    }

    @Override
    public String detail() {
        return "The model response violated the LLM safety policy.";
    }
}
