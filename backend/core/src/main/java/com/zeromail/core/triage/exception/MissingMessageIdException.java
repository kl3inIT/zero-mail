package com.zeromail.core.triage.exception;

/** Privacy-safe signal for an inbound message that cannot be threaded. */
public class MissingMessageIdException extends RuntimeException {

    public MissingMessageIdException() {
        super();
    }
}
