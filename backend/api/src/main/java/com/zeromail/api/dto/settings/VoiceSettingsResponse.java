package com.zeromail.api.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "writingStyle",
            "personalInstructions",
            "emailSignature",
            "tonePreset",
            "aiOutputLanguage"
        })
public record VoiceSettingsResponse(
        String writingStyle,
        String personalInstructions,
        String emailSignature,
        @Schema(allowableValues = {"PROFESSIONAL", "FRIENDLY", "CASUAL", "FORMAL", "CUSTOM"})
                String tonePreset,
        @Schema(allowableValues = {"vi", "en"}) String aiOutputLanguage) {

    public static VoiceSettingsResponse from(SettingsVoiceResult settingsVoiceResult) {
        return new VoiceSettingsResponse(
                settingsVoiceResult.writingStyle(),
                settingsVoiceResult.personalInstructions(),
                settingsVoiceResult.emailSignature(),
                settingsVoiceResult.tonePreset(),
                settingsVoiceResult.aiOutputLanguage());
    }
}
