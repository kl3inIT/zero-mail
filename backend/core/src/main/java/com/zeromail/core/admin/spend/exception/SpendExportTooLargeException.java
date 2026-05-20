package com.zeromail.core.admin.spend.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;

public class SpendExportTooLargeException extends AdminBusinessException {

    private final int estimatedRows;
    private final int maximumRows;

    public SpendExportTooLargeException(int estimatedRows, int maximumRows) {
        super("Spend export too large: " + estimatedRows + " > " + maximumRows);
        this.estimatedRows = estimatedRows;
        this.maximumRows = maximumRows;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.spend_export_too_large";
    }

    @Override
    public String logEvent() {
        return "admin_spend_export_too_large";
    }

    @Override
    public String detail() {
        return "The requested spend export exceeds the maximum row budget.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("estimatedRows", estimatedRows, "maximumRows", maximumRows);
    }
}
