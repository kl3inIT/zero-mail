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
import com.zeromail.core.analytics.projection.EmailAddressLoadProjection;
import com.zeromail.core.analytics.projection.ReplyBucketProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewMessage;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewReadUnavailableException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsSummaryQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSummaryQueryService.class);
    private static final int RECENT_INBOX_ANALYTICS_SAMPLE_SIZE = 100;
    private static final Duration RECENT_INBOX_ANALYTICS_FETCH_BUDGET = Duration.ofSeconds(5);

    private final AnalyticsSummaryReadRepository analyticsSummaryReadRepository;
    private final GmailPreviewReadService gmailPreviewReadService;

    public AnalyticsSummaryQueryService(
            AnalyticsSummaryReadRepository analyticsSummaryReadRepository,
            GmailPreviewReadService gmailPreviewReadService) {
        this.analyticsSummaryReadRepository =
                Objects.requireNonNull(
                        analyticsSummaryReadRepository,
                        "analyticsSummaryReadRepository must not be null");
        this.gmailPreviewReadService =
                Objects.requireNonNull(
                        gmailPreviewReadService, "gmailPreviewReadService must not be null");
    }

    public AnalyticsSummaryProjection summarize(UUID tenantId, TimeWindow window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TimeWindow requestedWindow = Objects.requireNonNull(window, "window must not be null");
        Timestamp windowStartInclusive = Timestamp.from(requestedWindow.startInclusive());
        Timestamp windowEndExclusive = Timestamp.from(requestedWindow.endExclusive());

        long volumeObserved =
                analyticsSummaryReadRepository.countObservedVolume(
                        tenantId, windowStartInclusive, windowEndExclusive);
        RecentInboxAnalytics recentInboxAnalytics =
                fetchRecentInboxAnalytics(
                        tenantId, requestedWindow.startInclusive(), requestedWindow.endExclusive());
        if (recentInboxAnalytics.available()) {
            volumeObserved = recentInboxAnalytics.volumeObserved();
        }
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
        if (recentInboxAnalytics.available()) {
            topSenders = recentInboxAnalytics.topSenders();
        }
        RecentSentAnalytics recentSentAnalytics =
                fetchRecentSentAnalytics(
                        tenantId, requestedWindow.startInclusive(), requestedWindow.endExclusive());
        List<EmailAddressLoadProjection> topRecipients = recentSentAnalytics.topRecipients();
        List<RuleHitProjection> ruleHits =
                analyticsSummaryReadRepository.findRuleHits(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<DailyLoadProjection> dailyLoad =
                analyticsSummaryReadRepository.findDailyLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        if (recentInboxAnalytics.available()) {
            dailyLoad = mergeObservedDailyLoad(dailyLoad, recentInboxAnalytics.dailyObserved());
        }
        List<ActionMixProjection> actionMix =
                analyticsSummaryReadRepository.findActionMix(
                        tenantId, windowStartInclusive, windowEndExclusive);
        List<DomainLoadProjection> domainLoad =
                analyticsSummaryReadRepository.findDomainLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        if (recentInboxAnalytics.available()) {
            domainLoad = recentInboxAnalytics.domainLoad();
        }
        List<CategoryLoadProjection> categoryLoad =
                analyticsSummaryReadRepository.findCategoryLoad(
                        tenantId, windowStartInclusive, windowEndExclusive);
        if (recentInboxAnalytics.available()) {
            categoryLoad = recentInboxAnalytics.categoryLoad();
        }
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
                topRecipients,
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

    private RecentInboxAnalytics fetchRecentInboxAnalytics(
            UUID tenantId, Instant windowStartInclusive, Instant windowEndExclusive) {
        try {
            List<GmailPreviewMessage> recentInboxMessages =
                    gmailPreviewReadService.fetchRecentInboxMessages(
                            tenantId,
                            RECENT_INBOX_ANALYTICS_SAMPLE_SIZE,
                            false,
                            RECENT_INBOX_ANALYTICS_FETCH_BUDGET);
            List<GmailPreviewMessage> messagesInWindow =
                    recentInboxMessages.stream()
                            .filter(
                                    gmailPreviewMessage ->
                                            !gmailPreviewMessage
                                                            .internalDate()
                                                            .isBefore(windowStartInclusive)
                                                    && gmailPreviewMessage
                                                            .internalDate()
                                                            .isBefore(windowEndExclusive))
                            .toList();
            return RecentInboxAnalytics.available(messagesInWindow);
        } catch (GmailPreviewReadUnavailableException unavailableException) {
            log.info(
                    "event=analytics_recent_inbox_unavailable tenantId={} reason={}",
                    tenantId,
                    unavailableException.reason());
            return RecentInboxAnalytics.unavailable();
        }
    }

    private RecentSentAnalytics fetchRecentSentAnalytics(
            UUID tenantId, Instant windowStartInclusive, Instant windowEndExclusive) {
        try {
            List<GmailPreviewMessage> recentSentMessages =
                    gmailPreviewReadService.fetchRecentSentMessages(
                            tenantId,
                            RECENT_INBOX_ANALYTICS_SAMPLE_SIZE,
                            false,
                            RECENT_INBOX_ANALYTICS_FETCH_BUDGET);
            List<GmailPreviewMessage> messagesInWindow =
                    recentSentMessages.stream()
                            .filter(
                                    gmailPreviewMessage ->
                                            !gmailPreviewMessage
                                                            .internalDate()
                                                            .isBefore(windowStartInclusive)
                                                    && gmailPreviewMessage
                                                            .internalDate()
                                                            .isBefore(windowEndExclusive))
                            .toList();
            return RecentSentAnalytics.available(messagesInWindow);
        } catch (GmailPreviewReadUnavailableException unavailableException) {
            log.info(
                    "event=analytics_recent_sent_unavailable tenantId={} reason={}",
                    tenantId,
                    unavailableException.reason());
            return RecentSentAnalytics.unavailable();
        }
    }

    private static List<DailyLoadProjection> mergeObservedDailyLoad(
            List<DailyLoadProjection> databaseDailyLoad, Map<String, Long> recentObservedByDay) {
        LinkedHashMap<String, DailyLoadProjection> dailyLoadByDay = new LinkedHashMap<>();
        for (DailyLoadProjection projection : databaseDailyLoad) {
            dailyLoadByDay.put(projection.day(), projection);
        }
        for (Map.Entry<String, Long> recentObservedEntry : recentObservedByDay.entrySet()) {
            DailyLoadProjection existingProjection =
                    dailyLoadByDay.get(recentObservedEntry.getKey());
            dailyLoadByDay.put(
                    recentObservedEntry.getKey(),
                    new DailyLoadProjection(
                            recentObservedEntry.getKey(),
                            recentObservedEntry.getValue(),
                            existingProjection == null ? 0L : existingProjection.applied(),
                            existingProjection == null ? 0L : existingProjection.reverted()));
        }
        return dailyLoadByDay.values().stream()
                .sorted(Comparator.comparing(DailyLoadProjection::day))
                .toList();
    }

    private record RecentInboxAnalytics(
            boolean available,
            long volumeObserved,
            List<TopSenderProjection> topSenders,
            Map<String, Long> dailyObserved,
            List<DomainLoadProjection> domainLoad,
            List<CategoryLoadProjection> categoryLoad) {

        private static RecentInboxAnalytics unavailable() {
            return new RecentInboxAnalytics(false, 0L, List.of(), Map.of(), List.of(), List.of());
        }

        private static RecentInboxAnalytics available(List<GmailPreviewMessage> messages) {
            return new RecentInboxAnalytics(
                    true,
                    messages.size(),
                    AnalyticsSummaryQueryService.topSenders(messages),
                    AnalyticsSummaryQueryService.dailyObserved(messages),
                    AnalyticsSummaryQueryService.domainLoad(messages),
                    AnalyticsSummaryQueryService.categoryLoad(messages));
        }

        private RecentInboxAnalytics {
            topSenders = List.copyOf(topSenders);
            dailyObserved = Map.copyOf(dailyObserved);
            domainLoad = List.copyOf(domainLoad);
            categoryLoad = List.copyOf(categoryLoad);
        }
    }

    private record RecentSentAnalytics(
            boolean available, List<EmailAddressLoadProjection> topRecipients) {

        private static RecentSentAnalytics unavailable() {
            return new RecentSentAnalytics(false, List.of());
        }

        private static RecentSentAnalytics available(List<GmailPreviewMessage> messages) {
            return new RecentSentAnalytics(
                    true, AnalyticsSummaryQueryService.topRecipients(messages));
        }

        private RecentSentAnalytics {
            topRecipients = List.copyOf(topRecipients);
        }
    }

    private static List<TopSenderProjection> topSenders(List<GmailPreviewMessage> messages) {
        return countByValue(
                        messages.stream()
                                .map(GmailPreviewMessage::sanitizedSenderEmail)
                                .filter(senderEmail -> !senderEmail.isBlank())
                                .toList())
                .entrySet()
                .stream()
                .sorted(countDescendingKeyAscending())
                .limit(10)
                .map(entry -> new TopSenderProjection(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, Long> dailyObserved(List<GmailPreviewMessage> messages) {
        return countByValue(
                messages.stream()
                        .map(
                                gmailPreviewMessage ->
                                        DateTimeFormatter.ISO_LOCAL_DATE.format(
                                                gmailPreviewMessage
                                                        .internalDate()
                                                        .atZone(ZoneOffset.UTC)))
                        .toList());
    }

    private static List<EmailAddressLoadProjection> topRecipients(
            List<GmailPreviewMessage> messages) {
        return countByValue(
                        messages.stream()
                                .flatMap(
                                        gmailPreviewMessage ->
                                                Stream.concat(
                                                        gmailPreviewMessage
                                                                .sanitizedToRecipientEmails()
                                                                .stream(),
                                                        gmailPreviewMessage
                                                                .sanitizedCcRecipientEmails()
                                                                .stream()))
                                .filter(recipientEmail -> !recipientEmail.isBlank())
                                .toList())
                .entrySet()
                .stream()
                .sorted(countDescendingKeyAscending())
                .limit(10)
                .map(entry -> new EmailAddressLoadProjection(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<DomainLoadProjection> domainLoad(List<GmailPreviewMessage> messages) {
        return countByValue(
                        messages.stream()
                                .map(GmailPreviewMessage::sanitizedSenderDomain)
                                .filter(senderDomain -> !senderDomain.isBlank())
                                .toList())
                .entrySet()
                .stream()
                .sorted(countDescendingKeyAscending())
                .limit(8)
                .map(entry -> new DomainLoadProjection(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<CategoryLoadProjection> categoryLoad(List<GmailPreviewMessage> messages) {
        return countByValue(
                        messages.stream()
                                .flatMap(
                                        gmailPreviewMessage ->
                                                gmailPreviewMessage.gmailCategories().stream())
                                .filter(category -> !category.isBlank())
                                .toList())
                .entrySet()
                .stream()
                .sorted(countDescendingKeyAscending())
                .map(entry -> new CategoryLoadProjection(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static LinkedHashMap<String, Long> countByValue(List<String> values) {
        LinkedHashMap<String, Long> countsByValue = new LinkedHashMap<>();
        for (String value : values) {
            countsByValue.merge(value, 1L, Long::sum);
        }
        return countsByValue;
    }

    private static Comparator<Map.Entry<String, Long>> countDescendingKeyAscending() {
        return Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey());
    }
}
