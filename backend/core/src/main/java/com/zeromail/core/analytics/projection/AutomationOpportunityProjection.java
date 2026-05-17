package com.zeromail.core.analytics.projection;

public record AutomationOpportunityProjection(
        long noRuleMatched, long failedActions, long pendingActions) {}
