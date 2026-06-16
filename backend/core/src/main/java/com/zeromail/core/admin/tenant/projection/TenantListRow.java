package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TenantListRow(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        String status,
        String gmailConnectionStatus,
        String spendBucket7d,
        Instant lastActivityAt,
        String lastActivityKind,
        int totalRulesCount,
        int enabledRulesCount,
        List<String> enabledRuleNames,
        int observedEmail30dCount,
        int triageAction30dCount,
        int failedTriageAction30dCount,
        int outboundAction30dCount,
        int blockedOutboundAction30dCount,
        int chatSessionCount,
        Instant lastChatSessionAt,
        int assistantAction30dCount,
        int llmCall30dCount,
        int creditBalance,
        int pubsubBacklogCount,
        String gmailWatchStatus,
        String telegramStatus,
        Instant telegramLastActiveAt,
        boolean autoSendRulesEnabled) {

    public TenantListRow {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(gmailConnectionStatus, "gmailConnectionStatus must not be null");
        Objects.requireNonNull(spendBucket7d, "spendBucket7d must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
        Objects.requireNonNull(lastActivityKind, "lastActivityKind must not be null");
        enabledRuleNames = List.copyOf(enabledRuleNames);
        Objects.requireNonNull(gmailWatchStatus, "gmailWatchStatus must not be null");
        Objects.requireNonNull(telegramStatus, "telegramStatus must not be null");
    }
}
