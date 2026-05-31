package com.zeromail.core.chat.usecases.settings;

public record SettingsVoiceResult(
        String writingStyle,
        String personalInstructions,
        String emailSignature,
        String aiOutputLanguage) {}
