package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class TriageAuditNotFoundException extends BusinessException {

    public TriageAuditNotFoundException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.TRIAGE_AUDIT_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "triage_audit_not_found";
    }

    @Override
    public String title() {
        return "Triage audit not found";
    }

    @Override
    public String detail() {
        return "The requested triage audit entry was not found.";
    }
}
