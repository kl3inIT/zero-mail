package com.zeromail.core.chat.projection;

import java.time.Instant;
import java.util.UUID;

public record ChatHistoryProjection(UUID id, String title, Instant updatedAt, int messageCount) {}
