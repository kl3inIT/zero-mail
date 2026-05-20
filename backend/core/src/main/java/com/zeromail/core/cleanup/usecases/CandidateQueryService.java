package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query for the bulk-unsubscribe candidate list (UNS-01).
 *
 * <p>CQRS-lite — direct {@link JdbcTemplate} over the {@code mail_message_observed} + {@code
 * sender_suppression} tables. Mirrors {@code AnalyticsSummaryQueryService} (same package shape,
 * same {@code @Transactional(readOnly = true)} discipline, same {@code @Service} stereotype). Lives
 * in {@code core.cleanup.usecases} so the analytics module never sees cleanup-specific column
 * shapes (D-17 module boundary).
 *
 * <p>Privacy invariant: this service never logs raw {@code senderEmail} values. Only count +
 * tenantId scoped to the {@code event=cleanup_candidates_queried} log line.
 */
@Service
@Transactional(readOnly = true)
public class CandidateQueryService {

    private static final Logger log = LoggerFactory.getLogger(CandidateQueryService.class);

    /**
     * Candidate aggregation SQL.
     *
     * <p>Filters {@code mail_message_observed} rows for the requesting tenant inside the window
     * where at least one of the {@code list_unsubscribe_*} columns shows a usable handle (URL or
     * mailto), groups by {@code sender_email}, and excludes senders the tenant has already
     * suppressed via the {@code sender_suppression} list (anti-join by either {@code sender_email}
     * or {@code sender_domain}).
     *
     * <p>{@code BOOL_OR} aggregates the per-message one-click flag across the group — a sender is
     * {@code ONE_CLICK} iff at least one observed message advertised the RFC 8058 trigger pair.
     * Order is highest count first, with stable tie-break by sender_email.
     */
    private static final String CANDIDATE_SQL =
            """
            SELECT
                mmo.sender_email,
                split_part(mmo.sender_email, '@', 2) AS sender_domain,
                COUNT(*) AS message_count,
                MAX(mmo.observed_at) AS last_seen_at,
                CASE
                    WHEN BOOL_OR(mmo.list_unsubscribe_one_click) THEN 'ONE_CLICK'
                    WHEN BOOL_OR(mmo.list_unsubscribe_mailto IS NOT NULL) THEN 'MAILTO'
                    ELSE 'NONE'
                END AS unsubscribe_method
            FROM mail_message_observed mmo
            WHERE mmo.tenant_id = ?
              AND mmo.observed_at >= ?
              AND mmo.observed_at < ?
              AND mmo.sender_email IS NOT NULL
              AND (mmo.list_unsubscribe_url IS NOT NULL OR mmo.list_unsubscribe_mailto IS NOT NULL)
              AND NOT EXISTS (
                  SELECT 1 FROM sender_suppression ss
                  WHERE ss.tenant_id = mmo.tenant_id
                    AND (
                        ss.sender_email = mmo.sender_email
                        OR ss.sender_domain = split_part(mmo.sender_email, '@', 2)
                    )
              )
            GROUP BY mmo.sender_email
            ORDER BY message_count DESC, mmo.sender_email ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CandidateQueryService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Return up to {@code limit} candidate senders observed in {@code [now - window, now)} that
     * still have at least one of {@code list_unsubscribe_url} / {@code list_unsubscribe_mailto}
     * populated and are NOT in the tenant's suppression list.
     *
     * <p>{@code limit} is hard-capped to {@link UnsubscribeCampaignPolicy#MAX_SENDERS_PER_CAMPAIGN}
     * so a runaway controller cap cannot exceed the campaign batch ceiling.
     */
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Duration window, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be a positive Duration, was " + window);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }
        int effectiveLimit = Math.min(limit, UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN);
        Instant now = clock.instant();
        Instant windowStartInclusive = now.minus(window);
        Timestamp windowStartTimestamp = Timestamp.from(windowStartInclusive);
        Timestamp windowEndTimestamp = Timestamp.from(now);

        List<UnsubscribeCandidateProjection> candidates =
                jdbcTemplate.query(
                        CANDIDATE_SQL,
                        (resultSet, rowNumber) -> {
                            String senderEmail = resultSet.getString("sender_email");
                            String senderDomain = resultSet.getString("sender_domain");
                            long messageCount = resultSet.getLong("message_count");
                            Instant lastSeenAt = resultSet.getTimestamp("last_seen_at").toInstant();
                            UnsubscribeMethod unsubscribeMethod =
                                    UnsubscribeMethod.fromId(
                                            resultSet.getString("unsubscribe_method"));
                            return new UnsubscribeCandidateProjection(
                                    senderEmail,
                                    senderDomain == null ? "" : senderDomain,
                                    messageCount,
                                    lastSeenAt,
                                    unsubscribeMethod,
                                    false);
                        },
                        tenantId,
                        windowStartTimestamp,
                        windowEndTimestamp,
                        effectiveLimit);

        log.info(
                "event=cleanup_candidates_queried tenantId={} window={} limit={} count={}",
                tenantId,
                window,
                effectiveLimit,
                candidates.size());
        return candidates;
    }
}
