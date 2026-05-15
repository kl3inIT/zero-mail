package com.zeromail.core.rules.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class RuleValidationException extends BusinessException {

    private final Reason reason;

    private RuleValidationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public static RuleValidationException notFound() {
        return new RuleValidationException(Reason.NOT_FOUND);
    }

    public static RuleValidationException previewRequired() {
        return new RuleValidationException(Reason.PREVIEW_REQUIRED);
    }

    public static RuleValidationException versionMismatch() {
        return new RuleValidationException(Reason.VERSION_MISMATCH);
    }

    public static RuleValidationException invalidReorder() {
        return new RuleValidationException(Reason.INVALID_REORDER);
    }

    public static RuleValidationException unsafeAction() {
        return new RuleValidationException(Reason.UNSAFE_ACTION);
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
        return "rules_validation_rejected";
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
        NOT_FOUND(
                ErrorClass.NOT_FOUND,
                ErrorCodes.RULES_NOT_FOUND,
                "rules.not_found",
                "Rule not found",
                "The requested rule was not found for the current tenant."),
        PREVIEW_REQUIRED(
                ErrorClass.CONFLICT,
                ErrorCodes.RULES_PREVIEW_REQUIRED,
                "rules.preview_required",
                "Rule preview required",
                "The current rule version must be previewed before enabling."),
        VERSION_MISMATCH(
                ErrorClass.CONFLICT,
                ErrorCodes.RULES_VERSION_MISMATCH,
                "rules.version_mismatch",
                "Rule version mismatch",
                "The rule version changed before the request completed."),
        INVALID_REORDER(
                ErrorClass.BAD_REQUEST,
                ErrorCodes.RULES_REORDER_INVALID,
                "rules.invalid_reorder",
                "Invalid rule order",
                "The reorder request must include the full current rule list."),
        UNSAFE_ACTION(
                ErrorClass.BAD_REQUEST,
                ErrorCodes.RULES_UNSAFE_ACTION,
                "rules.unsafe_action",
                "Unsafe rule action",
                "The rule contains an action outside the safe action allow-list.");

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
