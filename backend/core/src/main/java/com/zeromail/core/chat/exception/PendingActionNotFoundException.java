package com.zeromail.core.chat.exception;

@SuppressWarnings("unused")
public class PendingActionNotFoundException extends RuntimeException {

    public PendingActionNotFoundException() {
        super("Pending assistant action was not found.");
    }

    public PendingActionNotFoundException(String toolCallId) {
        super("Pending assistant action was not found.");
    }
}
