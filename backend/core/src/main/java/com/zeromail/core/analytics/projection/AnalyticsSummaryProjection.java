package com.zeromail.core.analytics.projection;

import java.util.List;
import java.util.Objects;

public record AnalyticsSummaryProjection(
        long volumeObserved,
        long volumeApplied,
        long timeSavedSeconds,
        List<TopSenderProjection> topSenders,
        List<EmailAddressLoadProjection> topRecipients,
        List<RuleHitProjection> ruleHits,
        List<DailyLoadProjection> dailyLoad,
        List<ActionMixProjection> actionMix,
        List<DomainLoadProjection> domainLoad,
        List<CategoryLoadProjection> categoryLoad,
        List<ReplyBucketProjection> replyBuckets,
        AutomationOpportunityProjection automationOpportunities) {

    public AnalyticsSummaryProjection(
            long volumeObserved,
            long volumeApplied,
            long timeSavedSeconds,
            List<TopSenderProjection> topSenders,
            List<RuleHitProjection> ruleHits) {
        this(
                volumeObserved,
                volumeApplied,
                timeSavedSeconds,
                topSenders,
                List.of(),
                ruleHits,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AutomationOpportunityProjection(0, 0, 0));
    }

    public AnalyticsSummaryProjection {
        topSenders = List.copyOf(topSenders);
        topRecipients = List.copyOf(topRecipients);
        ruleHits = List.copyOf(ruleHits);
        dailyLoad = List.copyOf(dailyLoad);
        actionMix = List.copyOf(actionMix);
        domainLoad = List.copyOf(domainLoad);
        categoryLoad = List.copyOf(categoryLoad);
        replyBuckets = List.copyOf(replyBuckets);
        automationOpportunities =
                Objects.requireNonNull(
                        automationOpportunities, "automationOpportunities must not be null");
    }
}
