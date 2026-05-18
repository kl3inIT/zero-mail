package com.zeromail.core.chat.exception;

@SuppressWarnings("unused")
public class StaleToolCallException extends RuntimeException {

    public StaleToolCallException() {
        super("Tool call is stale.");
    }
}
