package com.zeromail.core.inbox.usecases;

/**
 * Thrown when {@link InboxProjectionCursorCodec#decode(String)} rejects a cursor. The controller
 * layer maps this to a 400 response — Wave 1 plumbs the mapping; Wave 0 only surfaces the
 * exception type so callers can catch it.
 */
public class InvalidProjectionCursorException extends RuntimeException {

    public InvalidProjectionCursorException(String message) {
        super(message);
    }

    public InvalidProjectionCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
