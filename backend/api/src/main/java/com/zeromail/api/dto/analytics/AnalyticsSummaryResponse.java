package com.zeromail.api.dto.analytics;

import com.zeromail.core.analytics.projection.ActionMixProjection;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.AutomationOpportunityProjection;
import com.zeromail.core.analytics.projection.CategoryLoadProjection;
import com.zeromail.core.analytics.projection.DailyLoadProjection;
import com.zeromail.core.analytics.projection.DomainLoadProjection;
import com.zeromail.core.analytics.projection.ReplyBucketProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import java.util.List;

public record AnalyticsSummaryResponse(
        String window,
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderResponse> topSenders,
        List<RuleHitResponse> ruleHits,
        List<DailyLoadResponse> dailyLoad,
        List<ActionMixResponse> actionMix,
        List<DomainLoadResponse> domainLoad,
        List<CategoryLoadResponse> categoryLoad,
        List<ReplyBucketResponse> replyBuckets,
        AutomationOpportunityResponse automationOpportunities) {

    public AnalyticsSummaryResponse {
        topSenders = List.copyOf(topSenders);
        ruleHits = List.copyOf(ruleHits);
        dailyLoad = List.copyOf(dailyLoad);
        actionMix = List.copyOf(actionMix);
        domainLoad = List.copyOf(domainLoad);
        categoryLoad = List.copyOf(categoryLoad);
        replyBuckets = List.copyOf(replyBuckets);
    }

    public static AnalyticsSummaryResponse from(
            AnalyticsSummaryProjection projection, AnalyticsWindow window) {
        return new AnalyticsSummaryResponse(
                window.id(),
                projection.volumeObserved(),
                projection.volumeApplied(),
                projection.timeSavedSeconds(),
                projection.topSenders().stream().map(TopSenderResponse::from).toList(),
                projection.ruleHits().stream().map(RuleHitResponse::from).toList(),
                projection.dailyLoad().stream().map(DailyLoadResponse::from).toList(),
                projection.actionMix().stream().map(ActionMixResponse::from).toList(),
                projection.domainLoad().stream().map(DomainLoadResponse::from).toList(),
                projection.categoryLoad().stream().map(CategoryLoadResponse::from).toList(),
                projection.replyBuckets().stream().map(ReplyBucketResponse::from).toList(),
                AutomationOpportunityResponse.from(projection.automationOpportunities()));
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

    public record DailyLoadResponse(String day, long observed, long applied, long reverted) {
        private static DailyLoadResponse from(DailyLoadProjection projection) {
            return new DailyLoadResponse(
                    projection.day(),
                    projection.observed(),
                    projection.applied(),
                    projection.reverted());
        }
    }

    public record ActionMixResponse(String actionType, long applied, long reverted, long failed) {
        private static ActionMixResponse from(ActionMixProjection projection) {
            return new ActionMixResponse(
                    projection.actionType(),
                    projection.applied(),
                    projection.reverted(),
                    projection.failed());
        }
    }

    public record DomainLoadResponse(String domain, long count) {
        private static DomainLoadResponse from(DomainLoadProjection projection) {
            return new DomainLoadResponse(projection.domain(), projection.count());
        }
    }

    public record CategoryLoadResponse(String category, long count) {
        private static CategoryLoadResponse from(CategoryLoadProjection projection) {
            return new CategoryLoadResponse(projection.category(), projection.count());
        }
    }

    public record ReplyBucketResponse(String bucket, long count, long withDraft) {
        private static ReplyBucketResponse from(ReplyBucketProjection projection) {
            return new ReplyBucketResponse(
                    projection.bucket(), projection.count(), projection.withDraft());
        }
    }

    public record AutomationOpportunityResponse(
            long noRuleMatched, long failedActions, long pendingActions) {
        private static AutomationOpportunityResponse from(
                AutomationOpportunityProjection projection) {
            return new AutomationOpportunityResponse(
                    projection.noRuleMatched(),
                    projection.failedActions(),
                    projection.pendingActions());
        }
    }
}
