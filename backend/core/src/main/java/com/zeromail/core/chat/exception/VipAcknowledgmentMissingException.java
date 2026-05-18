package com.zeromail.core.chat.exception;

public class VipAcknowledgmentMissingException extends RuntimeException {

    public VipAcknowledgmentMissingException() {
        super("VIP recipient acknowledgment is required.");
    }
}
