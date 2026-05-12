package com.zeromail.core.draft.exception;

public class DraftGenerationFailedException extends RuntimeException {

    public DraftGenerationFailedException() {
        super();
    }

    public DraftGenerationFailedException(Throwable cause) {
        super(null, cause);
    }
}
