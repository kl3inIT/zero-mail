package com.zeromail.api.dto.settings;

import com.zeromail.core.chat.usecases.settings.VoiceGenerationResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"generatedStyle"})
public record GenerateFromSentResponse(String generatedStyle) {

    public static GenerateFromSentResponse from(VoiceGenerationResult voiceGenerationResult) {
        return new GenerateFromSentResponse(voiceGenerationResult.generatedStyle());
    }
}
