package com.zeromail.core.chat.usecases.settings;

public record SettingsBehaviorResult(
        boolean autoDraftReplies, String draftConfidence, boolean sensitiveDataProtection) {}
