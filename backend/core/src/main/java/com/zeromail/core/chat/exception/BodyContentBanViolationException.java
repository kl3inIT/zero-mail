package com.zeromail.core.chat.exception;

@SuppressWarnings("unused")
public class BodyContentBanViolationException extends RuntimeException {

    public BodyContentBanViolationException(Throwable cause) {
        super("Chat message parts violated the body-content persistence policy.", cause);
    }
}
