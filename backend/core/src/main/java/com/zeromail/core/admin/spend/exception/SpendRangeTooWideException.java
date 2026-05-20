package com.zeromail.core.admin.spend.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;

public class SpendRangeTooWideException extends AdminBusinessException {

    private final long requestedDays;
    private final long maximumDays;

    public SpendRangeTooWideException(long requestedDays, long maximumDays) {
        super("Spend range too wide: " + requestedDays + " > " + maximumDays);
        this.requestedDays = requestedDays;
        this.maximumDays = maximumDays;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.spend_range_too_wide";
    }

    @Override
    public String logEvent() {
        return "admin_spend_range_too_wide";
    }

    @Override
    public String detail() {
        return "The requested spend range exceeds the maximum allowed window.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("requestedDays", requestedDays, "maximumDays", maximumDays);
    }
}
