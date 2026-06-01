package com.zeromail.core.chat.usecases.settings;

import java.util.Objects;
import java.util.UUID;

public record VoiceGenerationCommand(UUID tenantId, int sampleSize) {

    public VoiceGenerationCommand {
        Objects.requireNonNull(tenantId, "tenantId");
    }
}
