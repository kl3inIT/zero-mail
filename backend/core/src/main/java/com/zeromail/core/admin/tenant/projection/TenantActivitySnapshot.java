package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;

public record TenantActivitySnapshot(
        int last30dRuleFireCount,
        int chatSessionCount,
        Instant lastChatSessionAt,
        String lastChatModelSelection) {}
