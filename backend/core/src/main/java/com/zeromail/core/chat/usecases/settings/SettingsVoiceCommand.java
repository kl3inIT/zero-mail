package com.zeromail.core.chat.usecases.settings;

public record SettingsVoiceCommand(
        String writingStyle,
        String personalInstructions,
        String emailSignature,
        String tonePreset,
        String aiOutputLanguage) {}
