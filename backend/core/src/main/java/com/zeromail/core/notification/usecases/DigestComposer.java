package com.zeromail.core.notification.usecases;

import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestRuleHit;
import com.zeromail.core.notification.domain.DigestTopSender;
import com.zeromail.core.notification.domain.DigestTotals;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DigestComposer {

    private static final Logger log = LoggerFactory.getLogger(DigestComposer.class);

    private final AnalyticsSummaryQueryService analyticsSummaryQueryService;

    public DigestComposer(AnalyticsSummaryQueryService analyticsSummaryQueryService) {
        this.analyticsSummaryQueryService =
                Objects.requireNonNull(
                        analyticsSummaryQueryService,
                        "analyticsSummaryQueryService must not be null");
    }

    public DigestPayload compose(
            UUID tenantId,
            ZoneId tenantZone,
            Locale tenantLocale,
            LocalDate digestDayLocal,
            Instant sendMoment,
            URI baseAppUrl) {
        long composeStartedNanos = System.nanoTime();
        try {
            Objects.requireNonNull(tenantZone, "tenantZone must not be null");
            AnalyticsSummaryProjection summary =
                    analyticsSummaryQueryService.summarize(
                            tenantId,
                            // Weekly digest: stats cover the same 7-day window as the content
                            // sections and the analytics deep-link (window=7d).
                            TimeWindow.between(sendMoment.minus(Duration.ofDays(7)), sendMoment));
            DigestTotals totals =
                    new DigestTotals(
                            summary.volumeObserved(),
                            summary.volumeApplied(),
                            summary.timeSavedSeconds());
            boolean zeroActivity = summary.volumeObserved() == 0 && summary.volumeApplied() == 0;
            return new DigestPayload(
                    tenantLocale,
                    tenantId,
                    digestDayLocal,
                    totals,
                    summary.topSenders().stream()
                            .map(
                                    topSender ->
                                            new DigestTopSender(
                                                    topSender.senderEmail(), topSender.count()))
                            .toList(),
                    summary.ruleHits().stream()
                            .map(
                                    ruleHit ->
                                            new DigestRuleHit(
                                                    ruleHit.ruleName(),
                                                    ruleHit.applied(),
                                                    ruleHit.reverted()))
                            // The analytics screen uses the full rule-hit query; the email digest
                            // intentionally shows only the top three entries.
                            .limit(3)
                            .toList(),
                    buildAppUrl(baseAppUrl, "analytics?source=digest&window=7d"),
                    buildAppUrl(baseAppUrl, "settings?section=notifications&source=digest"),
                    zeroActivity);
        } finally {
            long durationMs =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - composeStartedNanos);
            log.info(
                    "event=digest_compose_latency_ms tenantId={} durationMs={}",
                    tenantId,
                    durationMs);
        }
    }

    private static URI buildAppUrl(URI baseAppUrl, String relativePath) {
        String base = Objects.requireNonNull(baseAppUrl, "baseAppUrl must not be null").toString();
        URI normalizedBase = URI.create(base.endsWith("/") ? base : base + "/");
        return normalizedBase.resolve(relativePath);
    }
}
