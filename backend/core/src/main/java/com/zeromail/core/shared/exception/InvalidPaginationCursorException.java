package com.zeromail.core.shared.exception;

import com.zeromail.core.shared.error.ErrorCodes;

public class InvalidPaginationCursorException extends BusinessException {

    public InvalidPaginationCursorException(Throwable cause) {
        super("Invalid pagination cursor", cause);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.INVALID_CURSOR;
    }

    @Override
    public String logEvent() {
        return "invalid_pagination_cursor";
    }

    @Override
    public String title() {
        return "Invalid pagination cursor";
    }

    @Override
    public String detail() {
        return "The pagination cursor is malformed or no longer valid.";
    }
}
