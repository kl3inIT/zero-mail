package com.zeromail.core.admin.overview.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class AdminOverviewInvalidRangeException extends AdminBusinessException {

    public AdminOverviewInvalidRangeException(String message) {
        super(message);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.overview_invalid_range";
    }

    @Override
    public String logEvent() {
        return "admin_overview_invalid_range";
    }

    @Override
    public String detail() {
        return "The supplied admin overview time range is invalid.";
    }
}
