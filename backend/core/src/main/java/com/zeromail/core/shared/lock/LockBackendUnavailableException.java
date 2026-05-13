package com.zeromail.core.shared.lock;

public class LockBackendUnavailableException extends RuntimeException {

    public LockBackendUnavailableException() {
        super();
    }

    public LockBackendUnavailableException(Throwable cause) {
        super(null, cause);
    }
}
