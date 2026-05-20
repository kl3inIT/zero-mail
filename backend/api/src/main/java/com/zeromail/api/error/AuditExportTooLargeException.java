package com.zeromail.api.error;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class AuditExportTooLargeException extends AdminBusinessException {

    public AuditExportTooLargeException() {
        super("Admin audit export exceeds the 10000 row limit");
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.audit_export_too_large";
    }

    @Override
    public String logEvent() {
        return "admin_audit_export_too_large";
    }

    @Override
    public String detail() {
        return "The requested admin audit export exceeds the maximum row budget.";
    }
}
