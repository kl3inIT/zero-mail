package com.zeromail.core.gmail.service;

public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException(String message) {
        super(message);
    }
}
