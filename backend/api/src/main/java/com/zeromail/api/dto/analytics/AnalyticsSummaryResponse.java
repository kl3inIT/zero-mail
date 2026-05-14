package com.zeromail.api.dto.analytics;

import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import java.util.List;

public record AnalyticsSummaryResponse(
        String window,
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderResponse> topSenders,
        List<RuleHitResponse> ruleHits) {

    public AnalyticsSummaryResponse {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
    }

    public static AnalyticsSummaryResponse from(
            AnalyticsSummaryProjection projection, AnalyticsWindow window) {
        return new AnalyticsSummaryResponse(
                window.id(),
                projection.volumeObserved(),
                projection.volumeApplied(),
                projection.timeSavedSeconds(),
                projection.topSenders().stream().map(TopSenderResponse::from).toList(),
                projection.ruleHits().stream().map(RuleHitResponse::from).toList());
    }

    public record TopSenderResponse(String senderEmail, long count) {
        private static TopSenderResponse from(TopSenderProjection projection) {
            return new TopSenderResponse(projection.senderEmail(), projection.count());
        }
    }

    public record RuleHitResponse(String ruleName, long decisions, long applied, long reverted) {
        private static RuleHitResponse from(RuleHitProjection projection) {
            return new RuleHitResponse(
                    projection.ruleName(),
                    projection.decisions(),
                    projection.applied(),
                    projection.reverted());
        }
    }
}
