package com.zeromail.core.analytics.usecases;

import com.zeromail.core.analytics.domain.TimeSavedWeights;
import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.persistence.lowlevel.AnalyticsSummaryReadRepository;
import com.zeromail.core.analytics.projection.ActionMixProjection;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.AutomationOpportunityProjection;
import com.zeromail.core.analytics.projection.CategoryLoadProjection;
import com.zeromail.core.analytics.projection.DailyLoadProjection;
import com.zeromail.core.analytics.projection.DomainLoadProjection;
import com.zeromail.core.analytics.projection.ReplyBucketProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsSummaryQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSummaryQueryService.class);

    private final AnalyticsSummaryReadRepository analyticsSummaryReadRepository;

    public AnalyticsSummaryQueryService(
            AnalyticsSummaryReadRepository analyticsSummaryReadRepository) {
        this.analyticsSummaryReadRepository =
                Objects.requireNonNull(
                        analyticsSummaryReadRepository,
                        "analyticsSummaryReadRepository must not be null");
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryProjection summarize(UUID tenantId, TimeWindow window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TimeWindow requestedWindow = Objects.requireNonNull(window, "window must not be null");
        Timestamp windowStartInclusive = Timestamp.from(requestedWindow.startInclusive());
        Timestamp windowEndExclusive = Timestamp.from(requestedWindow.endExclusive());

        long volumeObserved =
                analyticsSummaryReadRepository.countObservedVolume(
                        tenantId, windowStartInclusive, windowEndExclusive);
        long volumeApplied =
                analyticsSummaryReadRepository.countAppliedVolume(
                        tenantId, windowStartInclusive, windowEndExclusive);
        Map<String, Long> appliedByActionType =
                analyticsSummaryReadRepository.findAppliedByActionType(
                        tenantId, windowStartInclusive, windowEndExclusive);
        long timeSavedSeconds = TimeSavedWeights.computeSeconds(appliedByActionType);
        List<TopSenderProjection> topSenders =
                analyticsSummaryReadRepository.findTopSenders(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<RuleHitProjection> ruleHits =
                analyticsSummaryReadRepository.findRuleHits(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<DailyLoadProjection> dailyLoad =
                analyticsSummaryReadRepository.findDailyLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<ActionMixProjection> actionMix =
                analyticsSummaryReadRepository.findActionMix(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<DomainLoadProjection> domainLoad =
                analyticsSummaryReadRepository.findDomainLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<CategoryLoadProjection> categoryLoad =
                analyticsSummaryReadRepository.findCategoryLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<ReplyBucketProjection> replyBuckets =
                analyticsSummaryReadRepository.findReplyBuckets(
                        tenantId, windowStartInclusive, windowEndExclusive);
        AutomationOpportunityProjection automationOpportunities =
                buildAutomationOpportunities(tenantId, windowStartInclusive, windowEndExclusive);

        log.info(
                "event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}",
                tenantId,
                requestedWindow.startInclusive(),
                requestedWindow.endExclusive());
        return new AnalyticsSummaryProjection(
                volumeObserved,
                volumeApplied,
                timeSavedSeconds,
                topSenders,
                ruleHits,
                dailyLoad,
                actionMix,
                domainLoad,
                categoryLoad,
                replyBuckets,
                automationOpportunities);
    }

    private AutomationOpportunityProjection buildAutomationOpportunities(
            UUID tenantId, Timestamp from, Timestamp to) {
        long noRuleMatched = analyticsSummaryReadRepository.countNoRuleMatched(tenantId, from, to);
        Map<String, Long> opportunityActions =
                analyticsSummaryReadRepository.findOpportunityActions(tenantId, from, to);
        return new AutomationOpportunityProjection(
                noRuleMatched,
                opportunityActions.getOrDefault("FAILED", 0L),
                opportunityActions.getOrDefault("PENDING", 0L));
    }
}
