package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.SenderWorkingSetDailyCount;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CleanupSenderProjectionService {

    private static final String SOURCE_OBSERVED = "OBSERVED";
    private static final String SOURCE_GMAIL_WORKING_SET = "GMAIL_WORKING_SET";

    private final JdbcTemplate jdbcTemplate;

    public CleanupSenderProjectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Instant fromInclusive, Instant toExclusive, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }

        return jdbcTemplate.query(
                """
                SELECT
                    csp.sender_email,
                    MAX(csp.sender_domain) AS sender_domain,
                    MAX(csp.sender_name) FILTER (WHERE csp.sender_name IS NOT NULL) AS sender_name,
                    SUM(csp.message_count) AS message_count,
                    SUM(csp.read_message_count) AS read_message_count,
                    MAX(csp.last_seen_at) AS last_seen_at,
                    CASE
                        WHEN BOOL_OR(csp.unsubscribe_method = 'ONE_CLICK') THEN 'ONE_CLICK'
                        WHEN BOOL_OR(csp.unsubscribe_method = 'MAILTO') THEN 'MAILTO'
                        ELSE 'NONE'
                    END AS unsubscribe_method,
                    MAX(css.status) AS sender_status
                FROM cleanup_sender_projection csp
                LEFT JOIN cleanup_sender_status css
                  ON css.tenant_id = csp.tenant_id
                 AND css.sender_email = csp.sender_email
                WHERE csp.tenant_id = ?
                  AND csp.last_seen_at >= ?
                  AND csp.last_seen_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM sender_suppression ss
                      WHERE ss.tenant_id = csp.tenant_id
                        AND (
                            ss.sender_email = csp.sender_email
                            OR ss.sender_domain = csp.sender_domain
                        )
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM unsubscribe_attempt ua
                      JOIN unsubscribe_campaign uc ON uc.id = ua.campaign_id
                      WHERE uc.tenant_id = csp.tenant_id
                        AND ua.sender_email = csp.sender_email
                        AND uc.reverted_at IS NULL
                        AND ua.state IN ('PENDING', 'RUNNING')
                  )
                GROUP BY csp.sender_email
                ORDER BY SUM(csp.message_count) DESC, csp.sender_email ASC
                LIMIT ?
                """,
                (resultSet, rowNumber) ->
                        new UnsubscribeCandidateProjection(
                                resultSet.getString("sender_email"),
                                Objects.requireNonNullElse(
                                        resultSet.getString("sender_domain"), ""),
                                resultSet.getString("sender_name"),
                                resultSet.getLong("message_count"),
                                resultSet.getLong("read_message_count"),
                                resultSet.getTimestamp("last_seen_at").toInstant(),
                                UnsubscribeMethod.fromId(resultSet.getString("unsubscribe_method")),
                                resultSet.getString("sender_status"),
                                false),
                tenantId,
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive),
                limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refreshFromObserved(UUID tenantId, Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        return jdbcTemplate.update(
                """
                INSERT INTO cleanup_sender_projection (
                    tenant_id, activity_date, sender_email, sender_domain, sender_name,
                    message_count, read_message_count, last_seen_at, unsubscribe_method, source,
                    refreshed_at)
                SELECT
                    mmo.tenant_id,
                    (mmo.observed_at AT TIME ZONE 'UTC')::date,
                    lower(mmo.sender_email),
                    split_part(lower(mmo.sender_email), '@', 2),
                    MAX(mmo.sender_name) FILTER (WHERE mmo.sender_name IS NOT NULL),
                    COUNT(*),
                    COUNT(*) FILTER (WHERE NOT ('UNREAD' = ANY(mmo.label_ids))),
                    MAX(mmo.observed_at),
                    CASE
                        WHEN BOOL_OR(mmo.list_unsubscribe_one_click) THEN 'ONE_CLICK'
                        WHEN BOOL_OR(mmo.list_unsubscribe_mailto IS NOT NULL) THEN 'MAILTO'
                        ELSE 'NONE'
                    END,
                    ?,
                    NOW()
                FROM mail_message_observed mmo
                WHERE mmo.tenant_id = ?
                  AND mmo.observed_at >= ?
                  AND mmo.observed_at < ?
                  AND mmo.sender_email IS NOT NULL
                GROUP BY mmo.tenant_id, (mmo.observed_at AT TIME ZONE 'UTC')::date,
                         lower(mmo.sender_email)
                ON CONFLICT (tenant_id, sender_email, activity_date)
                DO UPDATE SET
                    sender_domain = EXCLUDED.sender_domain,
                    sender_name = COALESCE(EXCLUDED.sender_name,
                                           cleanup_sender_projection.sender_name),
                    message_count = GREATEST(cleanup_sender_projection.message_count,
                                             EXCLUDED.message_count),
                    read_message_count = GREATEST(cleanup_sender_projection.read_message_count,
                                                  EXCLUDED.read_message_count),
                    last_seen_at = GREATEST(cleanup_sender_projection.last_seen_at,
                                            EXCLUDED.last_seen_at),
                    unsubscribe_method = CASE
                        WHEN cleanup_sender_projection.unsubscribe_method = 'ONE_CLICK'
                          OR EXCLUDED.unsubscribe_method = 'ONE_CLICK' THEN 'ONE_CLICK'
                        WHEN cleanup_sender_projection.unsubscribe_method = 'MAILTO'
                          OR EXCLUDED.unsubscribe_method = 'MAILTO' THEN 'MAILTO'
                        ELSE 'NONE'
                    END,
                    source = EXCLUDED.source,
                    refreshed_at = NOW()
                """,
                SOURCE_OBSERVED,
                tenantId,
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertWorkingSet(
            UUID tenantId, CleanupRecentInboxWorkingSetService.WorkingSet workingSet) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workingSet, "workingSet must not be null");
        for (CleanupRecentInboxWorkingSetService.SenderWorkingSet senderWorkingSet :
                workingSet.senders()) {
            upsertSenderWorkingSet(tenantId, senderWorkingSet);
        }
    }

    private void upsertSenderWorkingSet(
            UUID tenantId, CleanupRecentInboxWorkingSetService.SenderWorkingSet senderWorkingSet) {
        String senderEmail = senderWorkingSet.senderEmail().trim().toLowerCase(Locale.ROOT);
        if (senderEmail.isBlank()) {
            return;
        }
        String senderDomain = normalizeSenderDomain(senderWorkingSet.senderDomain());
        if (senderDomain.isBlank()) {
            senderDomain = senderDomain(senderEmail);
        }
        for (SenderWorkingSetDailyCount dailyCount : senderWorkingSet.dailyCounts()) {
            upsertSenderWorkingSetDay(
                    tenantId, senderWorkingSet, senderEmail, senderDomain, dailyCount);
        }
    }

    private void upsertSenderWorkingSetDay(
            UUID tenantId,
            CleanupRecentInboxWorkingSetService.SenderWorkingSet senderWorkingSet,
            String senderEmail,
            String senderDomain,
            SenderWorkingSetDailyCount dailyCount) {
        jdbcTemplate.update(
                """
                INSERT INTO cleanup_sender_projection (
                    tenant_id, activity_date, sender_email, sender_domain, sender_name,
                    message_count, read_message_count, last_seen_at, unsubscribe_method, source,
                    refreshed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, sender_email, activity_date)
                DO UPDATE SET
                    sender_domain = EXCLUDED.sender_domain,
                    sender_name = COALESCE(EXCLUDED.sender_name,
                                           cleanup_sender_projection.sender_name),
                    message_count = GREATEST(cleanup_sender_projection.message_count,
                                             EXCLUDED.message_count),
                    read_message_count = GREATEST(cleanup_sender_projection.read_message_count,
                                                  EXCLUDED.read_message_count),
                    last_seen_at = GREATEST(cleanup_sender_projection.last_seen_at,
                                            EXCLUDED.last_seen_at),
                    unsubscribe_method = CASE
                        WHEN cleanup_sender_projection.unsubscribe_method = 'ONE_CLICK'
                          OR EXCLUDED.unsubscribe_method = 'ONE_CLICK' THEN 'ONE_CLICK'
                        WHEN cleanup_sender_projection.unsubscribe_method = 'MAILTO'
                          OR EXCLUDED.unsubscribe_method = 'MAILTO' THEN 'MAILTO'
                        ELSE 'NONE'
                    END,
                    source = EXCLUDED.source,
                    refreshed_at = NOW()
                """,
                tenantId,
                Date.valueOf(dailyCount.activityDate()),
                senderEmail,
                senderDomain,
                senderWorkingSet.senderName(),
                dailyCount.messageCount(),
                dailyCount.readMessageCount(),
                Timestamp.from(dailyCount.lastSeenAt()),
                senderWorkingSet.unsubscribeMethod().id(),
                SOURCE_GMAIL_WORKING_SET);
    }

    private static String senderDomain(String senderEmail) {
        int atIndex = senderEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "";
        }
        return senderEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSenderDomain(String senderDomain) {
        if (senderDomain == null) {
            return "";
        }
        return senderDomain.trim().toLowerCase(Locale.ROOT);
    }
}
