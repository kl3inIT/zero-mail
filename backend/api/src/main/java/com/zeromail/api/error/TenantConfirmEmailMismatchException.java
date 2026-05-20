package com.zeromail.api.error;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public final class TenantConfirmEmailMismatchException extends AdminBusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.confirm_email_mismatch";
    }

    @Override
    public String logEvent() {
        return "admin_tenant_confirm_email_mismatch";
    }

    @Override
    public String detail() {
        return "The confirmation email does not match the targeted tenant.";
    }
}
