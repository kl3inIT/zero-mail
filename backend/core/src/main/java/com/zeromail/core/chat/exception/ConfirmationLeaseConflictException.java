package com.zeromail.core.chat.exception;

@SuppressWarnings("unused")
public class ConfirmationLeaseConflictException extends RuntimeException {

    public ConfirmationLeaseConflictException() {
        super("Confirmation lease is already held.");
    }
}
