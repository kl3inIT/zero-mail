package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantListRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "tenantDisplayName",
            "createdAt",
            "status",
            "gmailConnectionStatus",
            "gmailAccountCount",
            "connectedGmailAccountCount",
            "spendBucket7d",
            "lastActivityAt",
            "lastActivityKind",
            "totalRulesCount",
            "enabledRulesCount",
            "enabledRuleNames",
            "observedEmail30dCount",
            "triageAction30dCount",
            "failedTriageAction30dCount",
            "outboundAction30dCount",
            "blockedOutboundAction30dCount",
            "chatSessionCount",
            "assistantAction30dCount",
            "llmCall30dCount",
            "creditBalance",
            "pubsubBacklogCount",
            "gmailWatchStatus",
            "telegramStatus",
            "autoSendRulesEnabled"
        })
public record TenantListRowResponse(
        UUID tenantId,
        String tenantDisplayName,
        @Schema(nullable = true) String ownerEmail,
        Instant createdAt,
        @Schema(nullable = true) String gmailAccountEmail,
        int gmailAccountCount,
        int connectedGmailAccountCount,
        @Schema(allowableValues = {"ACTIVE", "PAUSED", "DISCONNECTED"}) String status,
        @Schema(allowableValues = {"CONNECTED", "DISCONNECTED"}) String gmailConnectionStatus,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String spendBucket7d,
        Instant lastActivityAt,
        @Schema(
                        allowableValues = {
                            "TENANT_CREATED",
                            "GMAIL_CONNECTION",
                            "RULE",
                            "GMAIL_OBSERVED",
                            "TRIAGE",
                            "CHAT",
                            "TELEGRAM",
                            "ASSISTANT_ACTION",
                            "LLM"
                        })
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
        @Schema(allowableValues = {"WATCHING", "EXPIRED", "NOT_WATCHING", "NO_CONNECTION"})
                String gmailWatchStatus,
        @Schema(allowableValues = {"CONNECTED", "BLOCKED", "DISCONNECTED", "NO_CONNECTION"})
                String telegramStatus,
        Instant telegramLastActiveAt,
        boolean autoSendRulesEnabled) {

    public static TenantListRowResponse from(TenantListRow tenantListRow) {
        return new TenantListRowResponse(
                tenantListRow.tenantId(),
                tenantListRow.tenantDisplayName(),
                tenantListRow.ownerEmail(),
                tenantListRow.createdAt(),
                tenantListRow.gmailAccountEmail(),
                tenantListRow.gmailAccountCount(),
                tenantListRow.connectedGmailAccountCount(),
                tenantListRow.status(),
                tenantListRow.gmailConnectionStatus(),
                tenantListRow.spendBucket7d(),
                tenantListRow.lastActivityAt(),
                tenantListRow.lastActivityKind(),
                tenantListRow.totalRulesCount(),
                tenantListRow.enabledRulesCount(),
                tenantListRow.enabledRuleNames(),
                tenantListRow.observedEmail30dCount(),
                tenantListRow.triageAction30dCount(),
                tenantListRow.failedTriageAction30dCount(),
                tenantListRow.outboundAction30dCount(),
                tenantListRow.blockedOutboundAction30dCount(),
                tenantListRow.chatSessionCount(),
                tenantListRow.lastChatSessionAt(),
                tenantListRow.assistantAction30dCount(),
                tenantListRow.llmCall30dCount(),
                tenantListRow.creditBalance(),
                tenantListRow.pubsubBacklogCount(),
                tenantListRow.gmailWatchStatus(),
                tenantListRow.telegramStatus(),
                tenantListRow.telegramLastActiveAt(),
                tenantListRow.autoSendRulesEnabled());
    }
}
