package com.zeromail.core.draft.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class DraftGenerationFailedException extends BusinessException {

    public DraftGenerationFailedException() {
        super();
    }

    public DraftGenerationFailedException(Throwable cause) {
        super(null, cause);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.UNPROCESSABLE;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.DRAFT_GENERATION_FAILED;
    }

    @Override
    public String logEvent() {
        return "draft_generation_failed";
    }

    @Override
    public String title() {
        return "Draft generation failed";
    }

    @Override
    public String detail() {
        return "A draft could not be generated for this thread.";
    }
}
