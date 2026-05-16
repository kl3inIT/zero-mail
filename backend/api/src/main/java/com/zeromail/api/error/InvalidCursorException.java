package com.zeromail.api.error;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class InvalidCursorException extends BusinessException {

    public InvalidCursorException(Throwable cause) {
        super(null, cause);
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
        return "invalid_cursor";
    }

    @Override
    public String title() {
        return "Invalid cursor";
    }

    @Override
    public String detail() {
        return "The pagination cursor is malformed.";
    }
}
