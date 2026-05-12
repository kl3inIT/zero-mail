package com.zeromail.core.triage.exception;

/** Privacy-safe signal for malformed reply-threading headers. */
public class ThreadingHeaderInvalidException extends RuntimeException {

    public ThreadingHeaderInvalidException() {
        super();
    }
}
