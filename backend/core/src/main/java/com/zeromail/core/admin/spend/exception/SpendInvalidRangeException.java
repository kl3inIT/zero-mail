package com.zeromail.core.admin.spend.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Thrown when the caller supplies an inverted ({@code from > to}) or impossibly-future ({@code to >
 * now + 1d}) spend-dashboard time range. WR-06: the backend is the source of truth for this
 * invariant; the frontend half-validates today but reactively fires with the raw values.
 */
public class SpendInvalidRangeException extends AdminBusinessException {

    public SpendInvalidRangeException(String message) {
        super(message);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.invalid_range";
    }

    @Override
    public String logEvent() {
        return "admin_spend_invalid_range";
    }

    @Override
    public String detail() {
        return "The supplied spend-dashboard time range is invalid.";
    }
}
