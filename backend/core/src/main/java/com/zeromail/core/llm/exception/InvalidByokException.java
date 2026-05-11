package com.zeromail.core.llm.exception;

/**
 * Thrown when BYOK credentials or endpoint policy validation fail.
 *
 * <p><b>Privacy invariant.</b> This exception carries no endpoint URL, API key, upstream response
 * body, or model output content. HTTP mapping is added in Plan 05b.
 */
public class InvalidByokException extends RuntimeException {

    public InvalidByokException() {
        super();
    }
}
