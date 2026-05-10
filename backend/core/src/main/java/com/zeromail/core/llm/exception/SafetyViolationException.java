package com.zeromail.core.llm.exception;
/**
 * Thrown when the LLM gateway rejects an action outside the allow-list
 * {@code {LABEL, ARCHIVE, SAVE_DRAFT}}.
 *
 * <p><b>Privacy invariant.</b> This exception carries NO rejected action name, NO tool-call
 * arguments, NO model output content. The HTTP layer maps it to 500 with
 * {@code code="error.llm.safety_violation"}; the frontend localizes without ever seeing the
 * rejected payload.
 *
 * <p><b>Defense-in-depth pairing.</b> Layer 1 enforcement (Spring AI
 * {@code toolChoice="required"} + {@code internalToolExecutionEnabled(false)}) is at the wire
 * level; this exception is the Layer 2 fail-closed signal that the validator caught a function
 * name outside the allow-list. Both layers must independently fail open for an unsafe action to
 * leak.
 */
public class SafetyViolationException extends RuntimeException {

    public SafetyViolationException() {
        super();
    }
}
