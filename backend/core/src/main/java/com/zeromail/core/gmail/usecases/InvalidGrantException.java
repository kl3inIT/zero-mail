package com.zeromail.core.gmail.usecases;

public class InvalidGrantException extends RuntimeException {

    public InvalidGrantException(String message) {
        super(message);
    }
}
