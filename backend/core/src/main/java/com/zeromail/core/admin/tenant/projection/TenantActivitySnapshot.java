package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.List;

public record TenantActivitySnapshot(
        int last30dRuleFireCount,
        int chatSessionCount,
        Instant lastChatSessionAt,
        String lastChatModelSelection,
        int totalActivity7dCount,
        Instant lastLoginAt,
        Integer totalAppDurationSeconds,
        List<TenantActivityEvent> events) {

    public TenantActivitySnapshot {
        events = List.copyOf(events);
    }
}
