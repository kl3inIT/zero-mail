package com.zeromail.core.outbound.usecases;

public class OutboundSendException extends RuntimeException {

    public OutboundSendException(Throwable cause) {
        super(cause);
    }
}
