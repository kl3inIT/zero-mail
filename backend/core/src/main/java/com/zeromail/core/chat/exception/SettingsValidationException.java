package com.zeromail.core.chat.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class SettingsValidationException extends BusinessException {

    private final String errorCode;
    private final String logEvent;
    private final String title;
    private final String detail;

    private SettingsValidationException(
            String errorCode, String logEvent, String title, String detail) {
        super(errorCode);
        this.errorCode = errorCode;
        this.logEvent = logEvent;
        this.title = title;
        this.detail = detail;
    }

    public static SettingsValidationException writingStyleTooShort() {
        return new SettingsValidationException(
                ErrorCodes.VOICE_WRITING_STYLE_TOO_SHORT,
                "settings_voice_writing_style_too_short",
                "Writing style too short",
                "The writing style must contain at least 200 words.");
    }

    public static SettingsValidationException writingStyleTooLong() {
        return new SettingsValidationException(
                ErrorCodes.VOICE_WRITING_STYLE_TOO_LONG,
                "settings_voice_writing_style_too_long",
                "Writing style too long",
                "The writing style must contain at most 500 words.");
    }

    public static SettingsValidationException personalInstructionsTooLong() {
        return new SettingsValidationException(
                ErrorCodes.VOICE_PERSONAL_INSTRUCTIONS_TOO_LONG,
                "settings_voice_personal_instructions_too_long",
                "Personal instructions too long",
                "The personal instructions exceed the maximum length.");
    }

    public static SettingsValidationException invalidDraftConfidence() {
        return new SettingsValidationException(
                ErrorCodes.BEHAVIOR_DRAFT_CONFIDENCE_INVALID,
                "settings_behavior_draft_confidence_invalid",
                "Invalid draft confidence",
                "The draft confidence is not supported.");
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
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
