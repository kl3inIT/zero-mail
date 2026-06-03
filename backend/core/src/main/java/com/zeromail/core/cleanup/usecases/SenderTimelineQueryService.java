package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.projection.SenderTimelineEntry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates {@code mail_message_observed} rows by UTC calendar day for the bulk-unsubscribe Stats
 * dialog timeline bar chart. Inbox Zero source path:
 * {@code apps/web/app/(app)/[emailAccountId]/stats/NewsletterModal.tsx}.
 *
 * <p>Privacy: only counts + sender domain are logged. The full {@code senderEmail} value is never
 * logged at this service boundary.
 */
@Service
@Transactional(readOnly = true)
public class SenderTimelineQueryService {

    private static final Logger log = LoggerFactory.getLogger(SenderTimelineQueryService.class);
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
    private final Clock clock;

    public SenderTimelineQueryService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

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
                                        resultSet
                                                .getDate("bucket_date")
                                                .toLocalDate(),
                                        resultSet.getLong("message_count")),
                        tenantId,
                        normalizedSenderEmail,
                        Timestamp.from(windowStart),
                        Timestamp.from(now));
        log.info(
                "event=cleanup_sender_timeline_queried tenantId={} senderDomain={} window={} bucketCount={}",
                tenantId,
                domainOf(normalizedSenderEmail),
                window,
                entries.size());
        return entries;
    }

    private static String domainOf(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        return atIndex < 0 ? "unknown" : senderEmail.substring(atIndex + 1);
    }

    @SuppressWarnings("unused")
    private static ZoneOffset utc() {
        return ZoneOffset.UTC;
    }
}
