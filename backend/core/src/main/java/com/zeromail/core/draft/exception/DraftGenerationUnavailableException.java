package com.zeromail.core.draft.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class DraftGenerationUnavailableException extends BusinessException {

    public DraftGenerationUnavailableException(Throwable cause) {
        super(null, cause);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.SERVICE_UNAVAILABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.DRAFT_GENERATION_UNAVAILABLE;
    }

    @Override
    public String logEvent() {
        return "draft_generation_unavailable";
    }

    @Override
    public String title() {
        return "Draft generation temporarily unavailable";
    }

    @Override
    public String detail() {
        return "Draft generation is temporarily unavailable. Try again later.";
    }
}
