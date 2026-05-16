package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class TriageAuditException extends BusinessException {

    private final Reason reason;

    private TriageAuditException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public static TriageAuditException unsupportedActionType() {
        return new TriageAuditException(Reason.UNSUPPORTED_ACTION_TYPE);
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public ErrorClass errorClass() {
        return reason.errorClass;
    }

    @Override
    public String errorCode() {
        return reason.errorCode;
    }

    @Override
    public String logEvent() {
        return "triage_audit_rejected";
    }

    @Override
    public String title() {
        return reason.title;
    }

    @Override
    public String detail() {
        return reason.detail;
    }

    public enum Reason {
        UNSUPPORTED_ACTION_TYPE(
                ErrorClass.CONFLICT,
                ErrorCodes.TRIAGE_UNDO_UNSUPPORTED_ACTION,
                "triage.unsupported_action_type",
                "Triage undo unsupported",
                "The triage action type cannot be undone safely.");

        private final ErrorClass errorClass;
        private final String errorCode;
        private final String message;
        private final String title;
        private final String detail;

        Reason(
                ErrorClass errorClass,
                String errorCode,
                String message,
                String title,
                String detail) {
            this.errorClass = errorClass;
            this.errorCode = errorCode;
            this.message = message;
            this.title = title;
            this.detail = detail;
        }
    }
}
