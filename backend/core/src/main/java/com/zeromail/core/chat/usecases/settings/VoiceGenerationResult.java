package com.zeromail.core.chat.usecases.settings;

import java.util.Objects;

public record VoiceGenerationResult(String generatedStyle) {

    public VoiceGenerationResult {
        generatedStyle = Objects.requireNonNullElse(generatedStyle, "");
    }

    public static VoiceGenerationResult empty() {
        return new VoiceGenerationResult("");
    }
}
