package com.zeromail.core.llm.exception;

/**
 * Thrown when a sanitization pipeline step fails. Aborts the gateway call.
 *
 * <p><b>Privacy invariant:</b> this exception carries no email content, no prompt bytes, no
 * completion bytes. Only the failing step name and the underlying cause are retained.
 */
public class SanitizationException extends RuntimeException {

    private final String stepName;

    public SanitizationException(String stepName, Throwable cause) {
        super(null, cause);
        this.stepName = stepName;
    }

    public String stepName() {
        return stepName;
    }
}
