package com.zeromail.core.chat.exception;

public class GmailSendFailedException extends RuntimeException {

    public GmailSendFailedException(Throwable cause) {
        super("Gmail send failed.", cause);
    }
}
