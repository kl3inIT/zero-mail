package com.zeromail.core.triage.exception;

/**
 * Privacy-safe safety rejection signal. Carries no rejected action name, message content, prompt,
 * completion, sender address, or model output.
 */
public class TriageSafetyViolationException extends RuntimeException {

    public TriageSafetyViolationException() {
        super();
    }
}
