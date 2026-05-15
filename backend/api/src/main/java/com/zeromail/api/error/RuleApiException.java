package com.zeromail.api.error;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class RuleApiException extends BusinessException {

    private final Reason reason;

    private RuleApiException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public static RuleApiException invalidCompileOutput() {
        return new RuleApiException(Reason.INVALID_COMPILE_OUTPUT);
    }

    public static RuleApiException clarificationRequired() {
        return new RuleApiException(Reason.CLARIFICATION_REQUIRED);
    }

    public static RuleApiException invalidSampleSize() {
        return new RuleApiException(Reason.INVALID_SAMPLE_SIZE);
    }

    public static RuleApiException invalidReorder() {
        return new RuleApiException(Reason.INVALID_REORDER);
    }

    public static RuleApiException unsafeAction() {
        return new RuleApiException(Reason.UNSAFE_ACTION);
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return reason.errorCode;
    }

    @Override
    public String logEvent() {
        return "rules_api_rejected";
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
        INVALID_COMPILE_OUTPUT(
                ErrorCodes.RULES_COMPILE_INVALID,
                "Invalid rule compile output",
                "The compiled rule payload is invalid."),
        CLARIFICATION_REQUIRED(
                ErrorCodes.RULES_COMPILE_CLARIFICATION_REQUIRED,
                "Rule clarification required",
                "The rule must be clarified before this operation can continue."),
        INVALID_SAMPLE_SIZE(
                ErrorCodes.RULES_PREVIEW_INVALID_SAMPLE_SIZE,
                "Invalid preview sample size",
                "Preview sample size must be one of the allowed values."),
        INVALID_REORDER(
                ErrorCodes.RULES_REORDER_INVALID,
                "Invalid rule order",
                "The reorder request must include the full current rule list."),
        UNSAFE_ACTION(
                ErrorCodes.RULES_UNSAFE_ACTION,
                "Unsafe rule action",
                "The rule contains an action outside the safe action allow-list.");

        private final String errorCode;
        private final String title;
        private final String detail;

        Reason(String errorCode, String title, String detail) {
            this.errorCode = errorCode;
            this.title = title;
            this.detail = detail;
        }
    }
}
