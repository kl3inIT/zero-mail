package com.zeromail.core.chat.usecases.settings;

public record SettingsBehaviorCommand(
        Boolean autoDraftReplies, String draftConfidence, Boolean sensitiveDataProtection) {}
