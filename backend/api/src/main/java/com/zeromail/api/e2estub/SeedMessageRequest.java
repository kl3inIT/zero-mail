package com.zeromail.api.e2estub;

public record SeedMessageRequest(
        String tenantId,
        String messageId,
        String threadId,
        String from,
        String subject,
        String body) {}
