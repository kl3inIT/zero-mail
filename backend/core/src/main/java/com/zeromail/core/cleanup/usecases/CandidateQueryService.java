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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query for the bulk-unsubscribe candidate list (UNS-01).
 *
 * <p>CQRS-lite — direct {@link JdbcTemplate} over the {@code mail_message_observed} + {@code
 * sender_suppression} tables as the source of truth. When the DB returns zero rows for a tenant
 * (typically a fresh onboarding before the observer pipeline catches up) the service falls back to
 * a live Gmail-preview read so the candidate table is never empty for a real inbox. Once the DB has
 * at least one observed row inside the window, the Gmail path is skipped — that keeps the Inbox
 * Zero-style UX where Archive doesn't make a sender disappear (DB snapshot persists even after
 * Gmail removes the INBOX label).
 *
 * <p>Privacy invariant: this service never logs raw {@code senderEmail} values. Only count +
 * tenantId scoped to the {@code event=cleanup_candidates_queried} log line.
 */
@Service
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
                        MAX(mmo.sender_name) FILTER (WHERE mmo.sender_name IS NOT NULL) AS sender_name,
                        COUNT(*) AS message_count,
                        COUNT(*) FILTER (WHERE NOT ('UNREAD' = ANY(mmo.label_ids))) AS read_message_count,
                        MAX(mmo.observed_at) AS last_seen_at,
                        CASE
                            WHEN BOOL_OR(mmo.list_unsubscribe_one_click) THEN 'ONE_CLICK'
                            WHEN BOOL_OR(mmo.list_unsubscribe_mailto IS NOT NULL) THEN 'MAILTO'
                            ELSE 'NONE'
                        END AS unsubscribe_method,
                        MAX(css.status) AS sender_status
                    FROM mail_message_observed mmo
                    LEFT JOIN cleanup_sender_status css
                      ON css.tenant_id = mmo.tenant_id
                     AND css.sender_email = mmo.sender_email
                    WHERE mmo.tenant_id = ?
                      AND mmo.observed_at >= ?
                      AND mmo.observed_at < ?
                      AND mmo.sender_email IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM sender_suppression ss
                          WHERE ss.tenant_id = mmo.tenant_id
                            AND (
                                ss.sender_email = mmo.sender_email
                                OR ss.sender_domain = split_part(mmo.sender_email, '@', 2)
                            )
                      )
                      AND NOT EXISTS (
                          SELECT 1
                          FROM unsubscribe_attempt ua
                          JOIN unsubscribe_campaign uc ON uc.id = ua.campaign_id
                          WHERE uc.tenant_id = mmo.tenant_id
                            AND ua.sender_email = mmo.sender_email
                            AND uc.reverted_at IS NULL
                            AND ua.state IN ('PENDING', 'RUNNING')
                      )
                    GROUP BY mmo.sender_email
                    ORDER BY message_count DESC, mmo.sender_email ASC
                    LIMIT ?
                    """;

    private final JdbcTemplate jdbcTemplate;
    private final CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService;
    private final Clock clock;

    public CandidateQueryService(
            JdbcTemplate jdbcTemplate,
            CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.cleanupRecentInboxWorkingSetService =
                Objects.requireNonNull(
                        cleanupRecentInboxWorkingSetService,
                        "cleanupRecentInboxWorkingSetService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Return up to {@code limit} candidate senders observed in {@code [now - window, now)} that
     * still have at least one of {@code list_unsubscribe_url} / {@code list_unsubscribe_mailto}
     * populated and are NOT in the tenant's suppression list.
     *
     * <p>{@code limit} is hard-capped to {@link UnsubscribeCampaignPolicy#MAX_CANDIDATE_SENDERS} so
     * the browsing table can load more than one execution batch without an unbounded query.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Duration window, int limit) {
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be a positive Duration, was " + window);
        }
        Instant now = clock.instant();
        return findCandidates(tenantId, now.minus(window), now, limit);
    }

    /**
     * Range-based overload powering the date-picker filter ("from {@code fromInclusive}" through
     * "to {@code toExclusive}"). Callers that want the legacy 7d/30d/90d preset continue to use the
     * {@link Duration}-based method above; the date picker UI calls this one directly so users can
     * target an arbitrary historical window (e.g. May 1 – May 15) instead of only "last N days from
     * now".
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Instant fromInclusive, Instant toExclusive, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be strictly before toExclusive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }
        int effectiveLimit = Math.min(limit, UnsubscribeCampaignPolicy.MAX_CANDIDATE_SENDERS);
        Duration window = Duration.between(fromInclusive, toExclusive);
        Timestamp windowStartTimestamp = Timestamp.from(fromInclusive);
        Timestamp windowEndTimestamp = Timestamp.from(toExclusive);

        List<UnsubscribeCandidateProjection> candidates =
                jdbcTemplate.query(
                        CANDIDATE_SQL,
                        (resultSet, rowNumber) -> {
                            String senderEmail = resultSet.getString("sender_email");
                            String senderDomain = resultSet.getString("sender_domain");
                            String senderName = resultSet.getString("sender_name");
                            long messageCount = resultSet.getLong("message_count");
                            long readMessageCount = resultSet.getLong("read_message_count");
                            Instant lastSeenAt = resultSet.getTimestamp("last_seen_at").toInstant();
                            UnsubscribeMethod unsubscribeMethod =
                                    UnsubscribeMethod.fromId(
                                            resultSet.getString("unsubscribe_method"));
                            String senderStatus = resultSet.getString("sender_status");
                            return new UnsubscribeCandidateProjection(
                                    senderEmail,
                                    senderDomain == null ? "" : senderDomain,
                                    senderName,
                                    messageCount,
                                    readMessageCount,
                                    lastSeenAt,
                                    unsubscribeMethod,
                                    senderStatus,
                                    false);
                        },
                        tenantId,
                        windowStartTimestamp,
                        windowEndTimestamp,
                        effectiveLimit);

        if (!candidates.isEmpty()) {
            log.info(
                    "event=cleanup_candidates_queried tenantId={} window={} limit={} count={} source=db",
                    tenantId,
                    window,
                    effectiveLimit,
                    candidates.size());
            return candidates;
        }

        // DB is empty for this tenant (observer pipeline has not populated mail_message_observed
        // yet for this window). Fall back to a live Gmail-preview read so a freshly-onboarded
        // tenant can browse senders immediately. Once the observer catches up the DB branch wins
        // — and after Archive, sender stays in the list because the DB snapshot persists.
        java.util.Optional<CleanupRecentInboxWorkingSetService.WorkingSet> recentInboxWorkingSet =
                cleanupRecentInboxWorkingSetService.findRecentInboxWorkingSet(tenantId, window);
        if (recentInboxWorkingSet.isPresent()) {
            List<UnsubscribeCandidateProjection> bootstrapCandidates =
                    recentInboxWorkingSet.orElseThrow().toCandidateProjections(effectiveLimit);
            log.info(
                    "event=cleanup_candidates_queried tenantId={} window={} limit={} count={} source=gmail_bootstrap",
                    tenantId,
                    window,
                    effectiveLimit,
                    bootstrapCandidates.size());
            return bootstrapCandidates;
        }

        log.info(
                "event=cleanup_candidates_queried tenantId={} window={} limit={} count=0 source=db",
                tenantId,
                window,
                effectiveLimit);
        return candidates;
    }
}
