package com.zeromail.core.chat.domain.parts;

import java.time.Instant;

public record AssistantTextPart(String partId, String text, Instant completedAt) implements Part {}
