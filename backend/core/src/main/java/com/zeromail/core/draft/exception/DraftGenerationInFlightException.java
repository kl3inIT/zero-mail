package com.zeromail.core.draft.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class DraftGenerationInFlightException extends BusinessException {

    public DraftGenerationInFlightException() {
        super();
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.DRAFT_GENERATION_IN_FLIGHT;
    }

    @Override
    public String logEvent() {
        return "draft_generation_rejected";
    }

    @Override
    public String title() {
        return "Draft generation already in flight";
    }

    @Override
    public String detail() {
        return "A draft is already being generated for this thread.";
    }
}
