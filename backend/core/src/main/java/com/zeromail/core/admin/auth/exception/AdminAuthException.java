package com.zeromail.core.admin.auth.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class AdminAuthException extends AdminBusinessException {

    public AdminAuthException(String message) {
        super(message);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.auth";
    }

    @Override
    public String logEvent() {
        return "admin_auth_failed";
    }

    @Override
    public String detail() {
        return "The admin authentication step could not be completed.";
    }
}
