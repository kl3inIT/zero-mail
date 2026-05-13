package com.zeromail.core.analytics.projection;

import java.util.List;

public record AnalyticsSummaryProjection(
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderProjection> topSenders,
        List<RuleHitProjection> ruleHits) {

    public AnalyticsSummaryProjection {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
    }
}
