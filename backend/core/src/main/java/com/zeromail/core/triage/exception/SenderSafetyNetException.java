package com.zeromail.core.triage.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class SenderSafetyNetException extends BusinessException {

    private final ErrorClass errorClass;
    private final String errorCode;
    private final String logEvent;
    private final String title;
    private final String detail;

    private SenderSafetyNetException(
            ErrorClass errorClass, String errorCode, String logEvent, String title, String detail) {
        super(errorCode);
        this.errorClass = errorClass;
        this.errorCode = errorCode;
        this.logEvent = logEvent;
        this.title = title;
        this.detail = detail;
    }

    public static SenderSafetyNetException patternInvalid() {
        return new SenderSafetyNetException(
                ErrorClass.BAD_REQUEST,
                ErrorCodes.SAFETY_NET_PATTERN_INVALID,
                "safety_net_pattern_invalid",
                "Invalid safety net pattern",
                "The safety net pattern must be an email address or domain pattern.");
    }

    public static SenderSafetyNetException observationNotDeletable() {
        return new SenderSafetyNetException(
                ErrorClass.FORBIDDEN,
                ErrorCodes.SAFETY_NET_OBSERVATION_NOT_DELETABLE,
                "safety_net_observation_not_deletable",
                "Safety net entry cannot be deleted",
                "Only user-created safety net entries can be deleted.");
    }

    public static SenderSafetyNetException notFound() {
        return new SenderSafetyNetException(
                ErrorClass.NOT_FOUND,
                ErrorCodes.SAFETY_NET_NOT_FOUND,
                "safety_net_not_found",
                "Safety net entry not found",
                "The requested safety net entry was not found.");
    }

    @Override
    public ErrorClass errorClass() {
        return errorClass;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public String logEvent() {
        return logEvent;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String detail() {
        return detail;
    }
}
