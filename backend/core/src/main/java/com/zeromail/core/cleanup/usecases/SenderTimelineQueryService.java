package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.projection.SenderMessageSummary;
import com.zeromail.core.cleanup.projection.SenderTimelineEntry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates {@code mail_message_observed} rows by UTC calendar day for the bulk-unsubscribe Stats
 * dialog timeline bar chart. Inbox Zero source path: {@code
 * apps/web/app/(app)/[emailAccountId]/stats/NewsletterModal.tsx}.
 *
 * <p>Like {@link CandidateQueryService}, the DB is the source of truth, but when it returns no rows
 * for the sender (fresh onboarding before the observer pipeline populates {@code
 * mail_message_observed}, or a sender that only exists in the live Gmail bootstrap working set) the
 * timeline falls back to a live Gmail read and buckets messages by their {@code internalDate}.
 * Without this fallback the chart was always empty for bootstrap senders even though the candidate
 * list showed them — the reported "stats don't work" symptom.
 *
 * <p>Privacy: only counts + sender domain are logged. The full {@code senderEmail} value is never
 * logged at this service boundary.
 */
@Service
public class SenderTimelineQueryService {

    private static final Logger log = LoggerFactory.getLogger(SenderTimelineQueryService.class);
    private static final Duration GMAIL_FETCH_BUDGET = Duration.ofSeconds(10);
    private static final int GMAIL_FALLBACK_LIMIT = 50;
    private static final String TIMELINE_SQL =
            """
                    SELECT
                        DATE_TRUNC('day', mmo.observed_at AT TIME ZONE 'UTC')::date AS bucket_date,
                        COUNT(*) AS message_count
                    FROM mail_message_observed mmo
                    WHERE mmo.tenant_id = ?
                      AND mmo.sender_email = ?
                      AND mmo.observed_at >= ?
                      AND mmo.observed_at < ?
                    GROUP BY bucket_date
                    ORDER BY bucket_date ASC
                    """;

    private final JdbcTemplate jdbcTemplate;
    private final SenderMessageReadService senderMessageReadService;
    private final Clock clock;

    public SenderTimelineQueryService(
            JdbcTemplate jdbcTemplate,
            SenderMessageReadService senderMessageReadService,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.senderMessageReadService =
                Objects.requireNonNull(
                        senderMessageReadService, "senderMessageReadService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SenderTimelineEntry> findTimeline(
            UUID tenantId, String senderEmail, Duration window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be a positive Duration, was " + window);
        }
        String normalizedSenderEmail = senderEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedSenderEmail.isEmpty()) {
            throw new IllegalArgumentException("senderEmail must not be blank");
        }
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);

        List<SenderTimelineEntry> entries =
                jdbcTemplate.query(
                        TIMELINE_SQL,
                        (resultSet, rowNumber) ->
                                new SenderTimelineEntry(
                                        resultSet.getDate("bucket_date").toLocalDate(),
                                        resultSet.getLong("message_count")),
                        tenantId,
                        normalizedSenderEmail,
                        Timestamp.from(windowStart),
                        Timestamp.from(now));
        if (!entries.isEmpty()) {
            log.info(
                    "event=cleanup_sender_timeline_queried tenantId={} senderDomain={} window={} bucketCount={} source=db",
                    tenantId,
                    domainOf(normalizedSenderEmail),
                    window,
                    entries.size());
            return entries;
        }

        // DB has no observed rows for this sender (typically a tenant whose candidate list is
        // served from the live Gmail bootstrap, so the sender was never persisted). Derive the
        // timeline directly from Gmail so the chart reflects a real inbox instead of staying blank.
        List<SenderTimelineEntry> gmailTimeline =
                buildTimelineFromGmail(tenantId, normalizedSenderEmail, windowStart, now);
        log.info(
                "event=cleanup_sender_timeline_queried tenantId={} senderDomain={} window={} bucketCount={} source=gmail_bootstrap",
                tenantId,
                domainOf(normalizedSenderEmail),
                window,
                gmailTimeline.size());
        return gmailTimeline;
    }

    private List<SenderTimelineEntry> buildTimelineFromGmail(
            UUID tenantId,
            String senderEmail,
            Instant windowStartInclusive,
            Instant windowEndExclusive) {
        List<SenderMessageSummary> messages;
        try {
            messages =
                    senderMessageReadService.fetchMessagesFromSender(
                            tenantId, senderEmail, false, GMAIL_FALLBACK_LIMIT, GMAIL_FETCH_BUDGET);
        } catch (SenderMessageReadService.SenderMessagesUnavailableException gmailUnavailable) {
            // No Gmail access (not connected / revoked / transient) — keep the chart empty rather
            // than failing the endpoint; the FE already renders an empty-state for zero buckets.
            return List.of();
        }
        Map<LocalDate, Long> countsByDay = new TreeMap<>();
        for (SenderMessageSummary message : messages) {
            Instant internalDate = message.internalDate();
            if (internalDate.isBefore(windowStartInclusive)
                    || !internalDate.isBefore(windowEndExclusive)) {
                continue;
            }
            LocalDate bucketDate = internalDate.atZone(ZoneOffset.UTC).toLocalDate();
            countsByDay.merge(bucketDate, 1L, Long::sum);
        }
        return countsByDay.entrySet().stream()
                .map(dayCount -> new SenderTimelineEntry(dayCount.getKey(), dayCount.getValue()))
                .toList();
    }

    private static String domainOf(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        return atIndex < 0 ? "unknown" : senderEmail.substring(atIndex + 1);
    }
}
