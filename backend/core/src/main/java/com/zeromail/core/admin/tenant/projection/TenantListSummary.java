package com.zeromail.core.admin.tenant.projection;

public record TenantListSummary(
        int totalCount,
        int activeCount,
        int pausedCount,
        int disconnectedCount,
        int gmailConnectedCount,
        int telegramConnectedCount,
        int activeLast24hCount,
        int activeLast7dCount,
        int gmailUnhealthyCount,
        int automationFailure30dCount,
        int outboundBlocked30dCount,
        int lowCreditCount) {}
